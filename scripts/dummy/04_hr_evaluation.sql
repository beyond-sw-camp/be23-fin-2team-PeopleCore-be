-- =====================================================================
-- HR Service 더미 평가 데이터 (성과평가 시즌/KPI/Goal/SelfEval/MgrEval/EvalGrade)
-- ---------------------------------------------------------------------
-- 선행 조건:
--   1) 회사 'peoplecore' 생성 완료
--   2) 01_hr_master_data.sql 실행 완료 (부서 7개 + 직급 6개 + 직책 4개 + WorkGroup)
--   3) 02_hr_employees.sql 실행 완료 (사원 100명)
--
-- 본 스크립트가 추가하는 것:
--   - KPI Option (CATEGORY 5종 + UNIT 5종)
--   - KPI Template (~75개, 부서×직급×방향 매트릭스 + grade=NULL 공통)
--   - 평가자 매핑 (emp_evaluator_global) — 모든 ACTIVE 사원의 평가자 = 대표(emp001)
--   - 시즌 6개 (CLOSED 4 / OPEN 1 / DRAFT 1) + Stage 30개
--   - Goal (KPI 2 + OKR 1) — 모든 ACTIVE 사원, 입사일 < 시즌 시작일 매칭
--   - SelfEvaluation (대부분 APPROVED, 일부 부분 반려)
--   - ManagerEvaluation (사원당 1건)
--   - EvalGrade (CLOSED는 locked, OPEN은 snapshot only)
--
-- [실행 방법]
--   $ mysql -u <user> -p peoplecore < 04_hr_evaluation.sql
--
-- [재실행 가능]
--   상단 cleanup 블록이 회사별 평가 데이터 모두 삭제 후 재INSERT
--   사원/부서 마스터는 건드리지 않음
-- =====================================================================

USE peoplecore;

SET @company_name := 'peoplecore';
SET @cid := (SELECT company_id FROM company WHERE company_name = @company_name);

SELECT
  IFNULL(BIN_TO_UUID(@cid),
         CONCAT('❌ 회사를 찾을 수 없습니다: ', @company_name)) AS resolved_company;

-- ▼ 부서 lookup (01에서 만든 dept_code 기준) ▼
SET @d_exec  := (SELECT dept_id FROM department WHERE company_id=@cid AND dept_code='EXEC');
SET @d_hr    := (SELECT dept_id FROM department WHERE company_id=@cid AND dept_code='HR');
SET @d_fin   := (SELECT dept_id FROM department WHERE company_id=@cid AND dept_code='FIN');
SET @d_dev   := (SELECT dept_id FROM department WHERE company_id=@cid AND dept_code='DEV');
SET @d_inf   := (SELECT dept_id FROM department WHERE company_id=@cid AND dept_code='INF');
SET @d_sales := (SELECT dept_id FROM department WHERE company_id=@cid AND dept_code='SALES');
SET @d_mkt   := (SELECT dept_id FROM department WHERE company_id=@cid AND dept_code='MKT');

-- ▼ 직급 lookup (01) ▼
SET @g1 := (SELECT grade_id FROM grade WHERE company_id=@cid AND grade_code='G1'); -- 사원
SET @g2 := (SELECT grade_id FROM grade WHERE company_id=@cid AND grade_code='G2'); -- 대리
SET @g3 := (SELECT grade_id FROM grade WHERE company_id=@cid AND grade_code='G3'); -- 과장
SET @g4 := (SELECT grade_id FROM grade WHERE company_id=@cid AND grade_code='G4'); -- 차장
SET @g5 := (SELECT grade_id FROM grade WHERE company_id=@cid AND grade_code='G5'); -- 부장
SET @g6 := (SELECT grade_id FROM grade WHERE company_id=@cid AND grade_code='G6'); -- 이사

-- ▼ 평가자(대표) lookup ▼
SET @e_ceo := (SELECT emp_id FROM employee WHERE company_id=@cid AND emp_num='EMP-2025-001');

-- ▼ 본부장 직책 (T-HEAD = 임원 = emp002~004) — 평가 데이터에서 제외 ▼
-- ▼ 대표 직책 (T-CEO = emp001) — 평가 데이터에서 제외 ▼
SET @t_head := (SELECT title_id FROM title WHERE company_id=@cid AND title_code='T-HEAD');
SET @t_ceo  := (SELECT title_id FROM title WHERE company_id=@cid AND title_code='T-CEO');

-- =====================================================================
-- ★ CLEANUP: 04 더미 해당 테이블 전체 초기화 (매 실행마다 완전히 새로 넣기)
--   사원/부서(AUDIT 제외)/직급/직책 마스터는 건드리지 않음
-- =====================================================================
SET FOREIGN_KEY_CHECKS = 0;

TRUNCATE TABLE calibration;
TRUNCATE TABLE self_evaluation_file;
TRUNCATE TABLE self_evaluation;
TRUNCATE TABLE manager_evaluation;
TRUNCATE TABLE eval_grade;
TRUNCATE TABLE goal;
TRUNCATE TABLE stage;
TRUNCATE TABLE season;
TRUNCATE TABLE emp_evaluator_global;
TRUNCATE TABLE kpi_template;
TRUNCATE TABLE kpi_option;
DELETE FROM department WHERE company_id = @cid AND dept_code = 'AUDIT';

SET FOREIGN_KEY_CHECKS = 1;

-- ▼ 감사실(AUDIT) — CLEANUP 이후 생성 (중복 방지) ▼
--   소규모 팀 시연용, 3명만 배치 → minTeamSize=5 미만이라 BE 가 z-score 보정 스킵
--   기존 부서에서 emp090/091/092 (마케팅팀 팀원) 을 이동시켜 만든다
INSERT INTO department (company_id, dept_name, dept_code, parent_dept_id, sort_order, is_use, created_at)
  VALUES (@cid, '감사실', 'AUDIT', NULL, 0, 'Y', NOW());
SET @d_audit := (SELECT dept_id FROM department WHERE company_id=@cid AND dept_code='AUDIT');

UPDATE employee
   SET dept_id = @d_audit
 WHERE company_id = @cid
   AND emp_num IN ('EMP-2025-090', 'EMP-2025-091', 'EMP-2025-092');

-- ▼ admin(emp001) 부서를 HR 로 이동 — admin 이 HR 부서장으로 평가 활동 ▼
--   원본 02_hr_employees.sql 은 admin 을 EXEC 에 박지만, 04 시드에서는 HR 로 옮김
--   EXEC 부서엔 본부장(T-HEAD) 3명만 남음 → 모두 평가 제외
UPDATE employee
SET dept_id = @d_hr
WHERE company_id = @cid AND emp_id = @e_ceo;

-- =====================================================================
-- STEP 1. KpiOption (CATEGORY 5 + UNIT 5)
-- =====================================================================
INSERT INTO kpi_option (company_id, type, option_value, sort_order, is_active) VALUES
  (@cid, 'CATEGORY', '업무성과', 1, true),
  (@cid, 'CATEGORY', '프로젝트', 2, true),
  (@cid, 'CATEGORY', '고객만족', 3, true),
  (@cid, 'CATEGORY', '품질',     4, true),
  (@cid, 'CATEGORY', '효율성',   5, true),
  (@cid, 'UNIT',     '%',        1, true),
  (@cid, 'UNIT',     '건',       2, true),
  (@cid, 'UNIT',     '점',       3, true),
  (@cid, 'UNIT',     '시간',     4, true),
  (@cid, 'UNIT',     '원',       5, true);

SET @cat_work  := (SELECT option_id FROM kpi_option WHERE company_id=@cid AND type='CATEGORY' AND option_value='업무성과');
SET @cat_proj  := (SELECT option_id FROM kpi_option WHERE company_id=@cid AND type='CATEGORY' AND option_value='프로젝트');
SET @cat_cust  := (SELECT option_id FROM kpi_option WHERE company_id=@cid AND type='CATEGORY' AND option_value='고객만족');
SET @cat_qual  := (SELECT option_id FROM kpi_option WHERE company_id=@cid AND type='CATEGORY' AND option_value='품질');
SET @cat_eff   := (SELECT option_id FROM kpi_option WHERE company_id=@cid AND type='CATEGORY' AND option_value='효율성');
SET @unit_pct  := (SELECT option_id FROM kpi_option WHERE company_id=@cid AND type='UNIT' AND option_value='%');
SET @unit_cnt  := (SELECT option_id FROM kpi_option WHERE company_id=@cid AND type='UNIT' AND option_value='건');
SET @unit_score:= (SELECT option_id FROM kpi_option WHERE company_id=@cid AND type='UNIT' AND option_value='점');
SET @unit_hour := (SELECT option_id FROM kpi_option WHERE company_id=@cid AND type='UNIT' AND option_value='시간');
SET @unit_won  := (SELECT option_id FROM kpi_option WHERE company_id=@cid AND type='UNIT' AND option_value='원');

-- =====================================================================
-- STEP 2. KpiTemplate (부서 7개 × 직급별 2~3개 + grade=NULL 공통 1~2개)
--   grade=NULL: 해당 부서 전 직급 공통 KPI
--   baseline: 정적값. 사내평균은 SelfEval actual_value 로 동적 산출 (백엔드 로직)
-- =====================================================================

-- ── 임원실 (EXEC) — 이사·본부장 모두 평가 제외이므로 KPI 박지 않음 ──
-- ── 인사팀 (HR) ──
INSERT INTO kpi_template (department_id, grade_id, category_option_id, unit_option_id, name, description, baseline, direction, is_active) VALUES
  (@d_hr, @g5, @cat_proj, @unit_cnt,   '조직문화 개선 활동 건수', '분기 조직문화 프로젝트',                3.00,    'UP',       true),
  (@d_hr, @g4, @cat_work, @unit_pct,   '핵심인재 이탈률',         '핵심인재 자발적 이직률',                5.00,    'DOWN',     true),
  (@d_hr, @g4, @cat_work, @unit_pct,   '평가 일정 준수율',        '평가 시즌 단계 일정 준수',              95.00,   'MAINTAIN', true),
  (@d_hr, @g3, @cat_work, @unit_pct,   '채용 전환율',             '지원자 대비 입사율',                    20.00,   'UP',       true),
  (@d_hr, @g3, @cat_eff,  @unit_hour,  '평균 채용 소요일',        '오픈→클로즈 평균(시간 환산)',           720.00,  'DOWN',     true),
  (@d_hr, @g2, @cat_eff,  @unit_hour,  '평가 문의 응답 시간',     '평가 관련 문의 평균 응답',              2.00,    'DOWN',     true),
  (@d_hr, @g2, @cat_work, @unit_cnt,   '교육 운영 건수',          '월간 운영 교육',                        4.00,    'UP',       true),
  (@d_hr, @g1, @cat_work, @unit_pct,   '신규 입사자 온보딩 완료율','분기 신규 입사 교육 이수율',           95.00,   'UP',       true),
  (@d_hr, @g1, @cat_work, @unit_pct,   '교육 참석률',             '운영 교육 참석률',                      90.00,   'UP',       true),
  (@d_hr, NULL, @cat_qual, @unit_pct,  '사내 규정 위반 처리율',   '규정 위반 처리율',                      100.00,  'UP',       true),
  (@d_hr, NULL, @cat_cust, @unit_score,'임직원 응대 만족도',      '사내 HR 문의 응대 만족도',              85.00,   'UP',       true);

-- ── 재무팀 (FIN) ──
INSERT INTO kpi_template (department_id, grade_id, category_option_id, unit_option_id, name, description, baseline, direction, is_active) VALUES
  (@d_fin, @g5, @cat_work, @unit_pct,   '자금 운용 수익률',        '운용 자산 수익률',                      3.00,    'UP',       true),
  (@d_fin, @g4, @cat_work, @unit_pct,   '결산 일정 준수율',        '월간 결산 일정 준수',                   100.00,  'MAINTAIN', true),
  (@d_fin, @g4, @cat_qual, @unit_cnt,   '외부감사 지적 건수',      '감사 지적사항',                         1.00,    'DOWN',     true),
  (@d_fin, @g3, @cat_work, @unit_pct,   '자금 조달 비용률',        '조달 비용 비율',                        4.00,    'DOWN',     true),
  (@d_fin, @g3, @cat_eff,  @unit_hour,  '비용 정산 처리 시간',     '청구 1건 처리 시간',                    4.00,    'DOWN',     true),
  (@d_fin, @g2, @cat_qual, @unit_cnt,   '세금계산서 오류 건수',    '발행 오류',                             1.00,    'DOWN',     true),
  (@d_fin, @g2, @cat_work, @unit_pct,   '월결산 마감일 준수',      '월결산 D+5 준수',                       100.00,  'MAINTAIN', true),
  (@d_fin, @g1, @cat_qual, @unit_pct,   '전표 입력 정확도',        '전표 입력 정확도',                      99.00,   'UP',       true),
  (@d_fin, @g1, @cat_work, @unit_cnt,   '비용 청구 처리 건수',     '주간 비용 청구 처리',                   50.00,   'UP',       true),
  (@d_fin, NULL, @cat_qual, @unit_cnt,  '세무 신고 오류 건수',     '세무 신고 오류',                        0.00,    'DOWN',     true),
  (@d_fin, NULL, @cat_qual, @unit_pct,  '회계 자료 보관 정확도',   '자료 보관 일치도',                      100.00,  'MAINTAIN', true);

-- ── 개발팀 (DEV) ──
INSERT INTO kpi_template (department_id, grade_id, category_option_id, unit_option_id, name, description, baseline, direction, is_active) VALUES
  (@d_dev, @g5, @cat_work, @unit_cnt,   '기술 전략 로드맵 수립',   '분기 기술 전략 수립 건수',              1.00,    'UP',       true),
  (@d_dev, @g5, @cat_work, @unit_pct,   '핵심인재 리텐션율',       '개발 인력 잔존율',                      90.00,   'UP',       true),
  (@d_dev, @g4, @cat_work, @unit_cnt,   '아키텍처 설계 검토',      '주요 시스템 설계 리뷰',                 4.00,    'UP',       true),
  (@d_dev, @g4, @cat_proj, @unit_cnt,   '기술 PoC 수행 건수',      '신규 기술 PoC',                         2.00,    'UP',       true),
  (@d_dev, @g3, @cat_work, @unit_pct,   '코드리뷰 참여율',         '팀 내 코드리뷰 참여',                   80.00,   'UP',       true),
  (@d_dev, @g3, @cat_proj, @unit_cnt,   '주요 기능 배포 건수',     '분기 릴리즈된 주요 기능',               10.00,   'UP',       true),
  (@d_dev, @g3, @cat_qual, @unit_cnt,   '기술부채 해소 건수',      '리팩토링·부채 해소',                    5.00,    'UP',       true),
  (@d_dev, @g2, @cat_eff,  @unit_hour,  '장애 복구 시간',          '평균 장애 복구',                        4.00,    'DOWN',     true),
  (@d_dev, @g2, @cat_work, @unit_pct,   '단위테스트 커버리지',     '커버리지 유지',                         75.00,   'MAINTAIN', true),
  (@d_dev, @g2, @cat_qual, @unit_score, '코드 품질 점수',          '정적분석 평균 점수',                    80.00,   'UP',       true),
  (@d_dev, @g1, @cat_work, @unit_pct,   '학습 미션 완료율',        '신규 사원 학습 트랙 완료율',            85.00,   'UP',       true),
  (@d_dev, @g1, @cat_work, @unit_pct,   '멘토링 참여율',           '주니어 멘토링 참여율',                  90.00,   'UP',       true),
  (@d_dev, @g1, @cat_qual, @unit_cnt,   '버그 발생 건수',          '본인 작성 코드 버그',                   5.00,    'DOWN',     true),
  (@d_dev, NULL, @cat_work, @unit_pct,  '일정 준수율',             '스프린트 일정 준수',                    95.00,   'MAINTAIN', true);

-- ── 인프라팀 (INF) ──
INSERT INTO kpi_template (department_id, grade_id, category_option_id, unit_option_id, name, description, baseline, direction, is_active) VALUES
  (@d_inf, @g5, @cat_eff,  @unit_pct,   '인프라 비용 효율화',      '연간 인프라 비용 절감률',               10.00,   'UP',       true),
  (@d_inf, @g4, @cat_qual, @unit_pct,   '시스템 가용성',           '월간 가용성',                           99.90,   'MAINTAIN', true),
  (@d_inf, @g4, @cat_qual, @unit_cnt,   '보안 사고 발생 건수',     '보안 인시던트',                         0.00,    'DOWN',     true),
  (@d_inf, @g3, @cat_eff,  @unit_hour,  '평균 장애 대응 시간',     '장애 발생 후 1차 대응까지',             1.00,    'DOWN',     true),
  (@d_inf, @g3, @cat_qual, @unit_pct,   '백업 성공률',             '일간 백업 성공률',                      100.00,  'MAINTAIN', true),
  (@d_inf, @g2, @cat_eff,  @unit_pct,   '인프라 비용 절감률',      '월간 비용 절감 비율',                   5.00,    'UP',       true),
  (@d_inf, @g2, @cat_eff,  @unit_hour,  '알람 처리 평균 시간',     '알람 1건당 처리 시간',                  0.50,    'DOWN',     true),
  (@d_inf, @g1, @cat_work, @unit_pct,   '모니터링 알람 처리율',    '발생 알람 대비 처리율',                 95.00,   'UP',       true),
  (@d_inf, @g1, @cat_work, @unit_cnt,   '점검 보고서 제출 건수',   '주간 점검 보고서',                      4.00,    'UP',       true),
  (@d_inf, NULL, @cat_qual, @unit_pct,  '보안 취약점 패치율',      '신규 취약점 패치 적용률',               95.00,   'UP',       true),
  (@d_inf, NULL, @cat_eff,  @unit_hour, 'MTTR (평균 복구 시간)',   '평균 복구 시간',                        2.00,    'DOWN',     true);

-- ── 영업팀 (SALES) ──
INSERT INTO kpi_template (department_id, grade_id, category_option_id, unit_option_id, name, description, baseline, direction, is_active) VALUES
  (@d_sales, @g5, @cat_proj, @unit_cnt,  '신규 시장 개척 건수',     '신규 산업·지역 진출',                   2.00,    'UP',       true),
  (@d_sales, @g5, @cat_work, @unit_pct,  '핵심 고객 거래액 증대',   '주요 고객 거래 증가율',                 15.00,   'UP',       true),
  (@d_sales, @g4, @cat_work, @unit_cnt,  '대형 계약 체결 건수',     '1억 이상 계약',                         3.00,    'UP',       true),
  (@d_sales, @g4, @cat_work, @unit_pct,  '매출 목표 달성률',        '분기 매출 KPI 달성',                    100.00,  'UP',       true),
  (@d_sales, @g3, @cat_work, @unit_won,  '영업 파이프라인 가치',    '예상 매출 합계',                        500000000.00, 'UP',  true),
  (@d_sales, @g3, @cat_eff,  @unit_hour, '견적 응답 시간',          '견적 요청 후 응답까지',                 4.00,    'DOWN',     true),
  (@d_sales, @g2, @cat_work, @unit_cnt,  '제안서 작성 건수',        '월간 제안서',                           6.00,    'UP',       true),
  (@d_sales, @g2, @cat_cust, @unit_cnt,  '신규 고객 확보',          '분기 신규 계약 고객',                   8.00,    'UP',       true),
  (@d_sales, @g2, @cat_eff,  @unit_cnt,  '미팅 횟수',               '주간 고객 미팅',                        5.00,    'UP',       true),
  (@d_sales, @g1, @cat_work, @unit_cnt,  '콜드콜 횟수',             '주간 콜드콜',                           30.00,   'UP',       true),
  (@d_sales, @g1, @cat_work, @unit_cnt,  '잠재고객 발굴 건수',      '신규 리드 발굴',                        20.00,   'UP',       true),
  (@d_sales, NULL, @cat_qual, @unit_pct, 'CRM 입력 정확도',         '거래 정보 정확도',                      95.00,   'UP',       true),
  (@d_sales, NULL, @cat_cust, @unit_pct, '고객 재계약률',           '기존 고객 재계약 유지율',               85.00,   'UP',       true);

-- ── 마케팅팀 (MKT) ──
INSERT INTO kpi_template (department_id, grade_id, category_option_id, unit_option_id, name, description, baseline, direction, is_active) VALUES
  (@d_mkt, @g5, @cat_work, @unit_pct,   '마케팅 ROI',              '캠페인 투자 대비 수익률',               150.00,  'UP',       true),
  (@d_mkt, @g5, @cat_work, @unit_pct,   '시장 점유율 증가',        '주력 카테고리 점유율 변화',             3.00,    'UP',       true),
  (@d_mkt, @g4, @cat_proj, @unit_cnt,   '신제품 런칭 캠페인 수',   '분기 런칭 캠페인',                      2.00,    'UP',       true),
  (@d_mkt, @g4, @cat_work, @unit_pct,   '캠페인 목표 달성률',      '분기 캠페인 KPI',                       90.00,   'UP',       true),
  (@d_mkt, @g3, @cat_work, @unit_cnt,   '월간 리드 생성 건수',     'MQL 생성 수',                           500.00,  'UP',       true),
  (@d_mkt, @g3, @cat_cust, @unit_score, '브랜드 만족도',           '브랜드 서베이 점수',                    80.00,   'UP',       true),
  (@d_mkt, @g2, @cat_work, @unit_pct,   '이메일 오픈율',           'EDM 평균 오픈율',                       25.00,   'UP',       true),
  (@d_mkt, @g2, @cat_eff,  @unit_pct,   '광고 전환율',             '광고 클릭→전환',                        5.00,    'UP',       true),
  (@d_mkt, @g1, @cat_work, @unit_pct,   'SNS 팔로워 증가율',       '월간 팔로워 증가',                      5.00,    'UP',       true),
  (@d_mkt, @g1, @cat_work, @unit_cnt,   '콘텐츠 발행 건수',        '월간 발행',                             10.00,   'UP',       true),
  (@d_mkt, NULL, @cat_work, @unit_pct,  '캠페인 일정 준수율',      '캠페인 런칭 일정 준수',                 95.00,   'MAINTAIN', true),
  (@d_mkt, NULL, @cat_qual, @unit_cnt,  '고객 컴플레인 건수',      '월간 고객 컴플레인',                    3.00,    'DOWN',     true);

-- ── 감사실 (AUDIT) — 소규모 팀, emp090(G4) / emp091(G3) / emp092(G3) 매칭용 ──
INSERT INTO kpi_template (department_id, grade_id, category_option_id, unit_option_id, name, description, baseline, direction, is_active) VALUES
  (@d_audit, @g4,  @cat_qual, @unit_cnt,  '감사 보고서 발행 건수',   '분기 내부감사 보고서',           4.00,   'UP',       true),
  (@d_audit, @g4,  @cat_qual, @unit_pct,  '감사 일정 준수율',        '감사 계획 일정 준수',            95.00,  'MAINTAIN', true),
  (@d_audit, @g3,  @cat_qual, @unit_cnt,  '내부통제 점검 건수',      '월간 통제활동 점검',             8.00,   'UP',       true),
  (@d_audit, @g3,  @cat_eff,  @unit_hour, '감사 응답 시간',          '감사 의뢰 응답까지',             24.00,  'DOWN',     true),
  (@d_audit, @g3,  @cat_qual, @unit_cnt,  '리스크 식별 건수',        '신규 리스크 등록',               3.00,   'UP',       true),
  (@d_audit, NULL, @cat_qual, @unit_pct,  '컴플라이언스 준수율',     '규정 준수율',                    100.00, 'MAINTAIN', true),
  (@d_audit, NULL, @cat_qual, @unit_cnt,  '시정 권고 이행 건수',     '권고사항 이행',                  5.00,   'UP',       true);

-- =====================================================================
-- STEP 3. emp_evaluator_global (부서별 최고 직급 사원이 부서원의 평가자)
--   ▷ 일반 사원 → 본인 부서 최고 직급 사원이 evaluator
--   ▷ 부서 평가자 본인 → 대표(@e_ceo) 가 evaluator
--   ▷ 대표(@e_ceo) → is_excluded=true (자기 자신 평가 불가)
--   ※ 부서별 최고 직급:
--     EXEC=이사 / HR/FIN/DEV/SALES=부장 / INF/MKT=차장
--     동률 시 emp_id 작은 사람 (가장 오래 된 사원) 채택
-- =====================================================================

-- 부서별 1순위(부서장) + 2순위 임시 테이블
--   ▷ 1순위 = 그 부서 최고 직급 (부서장)
--   ▷ 2순위 = 그 부서 두번째 직급 (부서장의 평가자)
--   ▷ 본부장(T-HEAD = 임원) 제외
--   백엔드 룰: 평가자는 같은 부서 사원만 가능
DROP TEMPORARY TABLE IF EXISTS tmp_dept_ranked;
CREATE TEMPORARY TABLE tmp_dept_ranked AS
SELECT
  e.emp_id,
  e.dept_id,
  ROW_NUMBER() OVER (PARTITION BY e.dept_id ORDER BY g.grade_order DESC, e.emp_id) AS rn
FROM employee e
JOIN grade g ON g.grade_id = e.grade_id
WHERE e.company_id = @cid
  AND e.emp_status = 'ACTIVE'
  AND e.title_id NOT IN (@t_head, @t_ceo);

-- MySQL 은 한 쿼리에서 같은 temp 테이블을 두 번 참조 못 하므로 복제본 생성
DROP TEMPORARY TABLE IF EXISTS tmp_dept_ranked_2;
CREATE TEMPORARY TABLE tmp_dept_ranked_2 AS SELECT * FROM tmp_dept_ranked;

DROP TEMPORARY TABLE IF EXISTS tmp_dept_evaluator;
CREATE TEMPORARY TABLE tmp_dept_evaluator AS
SELECT
  r1.dept_id,
  r1.emp_id AS evaluator_emp_id,                  -- 1순위 = 부서장
  r2.emp_id AS evaluator_for_evaluator            -- 2순위 = 부서장의 평가자
FROM tmp_dept_ranked r1
LEFT JOIN tmp_dept_ranked_2 r2 ON r2.dept_id = r1.dept_id AND r2.rn = 2
WHERE r1.rn = 1;

-- 평가자 매핑 (모든 사원은 같은 부서 내 평가자 매핑)
--   ▷ 일반 사원 → 부서장(1순위) 이 평가
--   ▷ 부서장 → 같은 부서 차순위(2순위) 가 평가 (admin 포함, 같은 부서 룰 충족)
--   ▷ 본부장(T-HEAD) 제외
INSERT INTO emp_evaluator_global (company_id, evaluatee_emp_id, evaluator_emp_id, is_excluded)
SELECT
  @cid,
  e.emp_id,
  CASE
    WHEN e.emp_id = de.evaluator_emp_id THEN de.evaluator_for_evaluator   -- 부서장은 차순위가 평가
    ELSE de.evaluator_emp_id                                              -- 일반 사원은 부서장이 평가
  END,
  false
FROM employee e
LEFT JOIN tmp_dept_evaluator de ON de.dept_id = e.dept_id
WHERE e.company_id = @cid
  AND e.emp_status = 'ACTIVE'
  AND e.title_id NOT IN (@t_head, @t_ceo);

-- =====================================================================
-- STEP 4. Season 6개 + Stage 30개
--   2024H1 / 2024H2 / 2025H1 / 2025H2: CLOSED (모든 stage FINISHED)
--   2026H1: OPEN (1~3 FINISHED, 4=GRADING IN_PROGRESS, 5 WAITING) ← 자동재산정 대기
--   2026H2: DRAFT (모든 stage WAITING)
-- =====================================================================
INSERT INTO season (company_id, name, period, start_date, end_date, status, finalized_at, form_snapshot, form_version) VALUES
  (@cid, '2024년 상반기 성과평가', 'FIRST_HALF', '2024-01-01', '2024-06-30', 'CLOSED', '2024-07-05 10:00:00',
   '{"itemList":[{"id":"self","name":"자기평가","weight":30,"locked":true,"enabled":true},{"id":"manager","name":"상위자평가","weight":70,"locked":true,"enabled":true}],"gradeRules":[{"id":"S","label":"S","ratio":10},{"id":"A","label":"A","ratio":20},{"id":"B","label":"B","ratio":40},{"id":"C","label":"C","ratio":20},{"id":"D","label":"D","ratio":10}],"adjustments":[{"id":"late","name":"지각","points":-1,"enabled":true},{"id":"absence","name":"무단결근","points":-3,"enabled":true}],"rawScoreTable":[{"gradeId":"S","rawScore":95},{"gradeId":"A","rawScore":85},{"gradeId":"B","rawScore":75},{"gradeId":"C","rawScore":65},{"gradeId":"D","rawScore":50}],"kpiScoring":{"cap":120,"scaleTo":100,"maintainTolerance":0,"underperformanceThreshold":0,"underperformanceFactor":1.0},"useBiasAdjustment":true,"biasWeight":1.00,"minTeamSize":5}',
   1),
  (@cid, '2024년 하반기 성과평가', 'SECOND_HALF', '2024-07-01', '2024-12-31', 'CLOSED', '2025-01-05 10:00:00',
   '{"itemList":[{"id":"self","name":"자기평가","weight":30,"locked":true,"enabled":true},{"id":"manager","name":"상위자평가","weight":70,"locked":true,"enabled":true}],"gradeRules":[{"id":"S","label":"S","ratio":10},{"id":"A","label":"A","ratio":20},{"id":"B","label":"B","ratio":40},{"id":"C","label":"C","ratio":20},{"id":"D","label":"D","ratio":10}],"adjustments":[{"id":"late","name":"지각","points":-1,"enabled":true},{"id":"absence","name":"무단결근","points":-3,"enabled":true}],"rawScoreTable":[{"gradeId":"S","rawScore":95},{"gradeId":"A","rawScore":85},{"gradeId":"B","rawScore":75},{"gradeId":"C","rawScore":65},{"gradeId":"D","rawScore":50}],"kpiScoring":{"cap":120,"scaleTo":100,"maintainTolerance":0,"underperformanceThreshold":0,"underperformanceFactor":1.0},"useBiasAdjustment":true,"biasWeight":1.00,"minTeamSize":5}',
   1),
  (@cid, '2025년 상반기 성과평가', 'FIRST_HALF', '2025-01-01', '2025-06-30', 'CLOSED', '2025-07-05 10:00:00',
   '{"itemList":[{"id":"self","name":"자기평가","weight":30,"locked":true,"enabled":true},{"id":"manager","name":"상위자평가","weight":70,"locked":true,"enabled":true}],"gradeRules":[{"id":"S","label":"S","ratio":10},{"id":"A","label":"A","ratio":20},{"id":"B","label":"B","ratio":40},{"id":"C","label":"C","ratio":20},{"id":"D","label":"D","ratio":10}],"rawScoreTable":[{"gradeId":"S","rawScore":95},{"gradeId":"A","rawScore":85},{"gradeId":"B","rawScore":75},{"gradeId":"C","rawScore":65},{"gradeId":"D","rawScore":50}],"kpiScoring":{"cap":120,"scaleTo":100,"maintainTolerance":0,"underperformanceThreshold":0,"underperformanceFactor":1.0},"useBiasAdjustment":true,"biasWeight":1.00,"minTeamSize":5}',
   1),
  (@cid, '2025년 하반기 성과평가', 'SECOND_HALF', '2025-07-01', '2025-12-31', 'CLOSED', '2026-01-05 10:00:00',
   '{"itemList":[{"id":"self","name":"자기평가","weight":30,"locked":true,"enabled":true},{"id":"manager","name":"상위자평가","weight":70,"locked":true,"enabled":true}],"gradeRules":[{"id":"S","label":"S","ratio":10},{"id":"A","label":"A","ratio":20},{"id":"B","label":"B","ratio":40},{"id":"C","label":"C","ratio":20},{"id":"D","label":"D","ratio":10}],"rawScoreTable":[{"gradeId":"S","rawScore":95},{"gradeId":"A","rawScore":85},{"gradeId":"B","rawScore":75},{"gradeId":"C","rawScore":65},{"gradeId":"D","rawScore":50}],"kpiScoring":{"cap":120,"scaleTo":100,"maintainTolerance":0,"underperformanceThreshold":0,"underperformanceFactor":1.0},"useBiasAdjustment":true,"biasWeight":1.00,"minTeamSize":5}',
   1),
  (@cid, '2026년 상반기 성과평가', 'FIRST_HALF', '2026-01-01', '2026-06-30', 'OPEN', NULL,
   '{"itemList":[{"id":"self","name":"자기평가","weight":30,"locked":true,"enabled":true},{"id":"manager","name":"상위자평가","weight":70,"locked":true,"enabled":true}],"gradeRules":[{"id":"S","label":"S","ratio":10},{"id":"A","label":"A","ratio":20},{"id":"B","label":"B","ratio":40},{"id":"C","label":"C","ratio":20},{"id":"D","label":"D","ratio":10}],"rawScoreTable":[{"gradeId":"S","rawScore":95},{"gradeId":"A","rawScore":85},{"gradeId":"B","rawScore":75},{"gradeId":"C","rawScore":65},{"gradeId":"D","rawScore":50}],"kpiScoring":{"cap":120,"scaleTo":100,"maintainTolerance":0,"underperformanceThreshold":0,"underperformanceFactor":1.0},"useBiasAdjustment":true,"biasWeight":1.00,"minTeamSize":5}',
   1),
  (@cid, '2026년 하반기 성과평가', 'SECOND_HALF', '2026-07-01', '2026-12-31', 'DRAFT', NULL, NULL, NULL);

SET @s_2024h1 := (SELECT season_id FROM season WHERE company_id=@cid AND name='2024년 상반기 성과평가');
SET @s_2024h2 := (SELECT season_id FROM season WHERE company_id=@cid AND name='2024년 하반기 성과평가');
SET @s_2025h1 := (SELECT season_id FROM season WHERE company_id=@cid AND name='2025년 상반기 성과평가');
SET @s_2025h2 := (SELECT season_id FROM season WHERE company_id=@cid AND name='2025년 하반기 성과평가');
SET @s_2026h1 := (SELECT season_id FROM season WHERE company_id=@cid AND name='2026년 상반기 성과평가');
SET @s_2026h2 := (SELECT season_id FROM season WHERE company_id=@cid AND name='2026년 하반기 성과평가');

-- Stage (6 시즌 × 5단계 = 30개)
INSERT INTO stage (season_id, name, order_no, stage_type, start_date, end_date, status) VALUES
  -- 2024H1 (CLOSED, 모두 FINISHED)
  (@s_2024h1, '목표등록',   1, 'GOAL_ENTRY',   '2024-01-01', '2024-01-15', 'FINISHED'),
  (@s_2024h1, '자기평가',   2, 'EVALUATION',   '2024-06-01', '2024-06-10', 'FINISHED'),
  (@s_2024h1, '상위자평가', 3, 'EVALUATION',   '2024-06-11', '2024-06-20', 'FINISHED'),
  (@s_2024h1, '등급산정',   4, 'GRADING',      '2024-06-21', '2024-06-28', 'FINISHED'),
  (@s_2024h1, '결과확정',   5, 'FINALIZATION', '2024-06-29', '2024-06-30', 'FINISHED'),
  -- 2024H2 (CLOSED, 모두 FINISHED)
  (@s_2024h2, '목표등록',   1, 'GOAL_ENTRY',   '2024-07-01', '2024-07-15', 'FINISHED'),
  (@s_2024h2, '자기평가',   2, 'EVALUATION',   '2024-12-01', '2024-12-10', 'FINISHED'),
  (@s_2024h2, '상위자평가', 3, 'EVALUATION',   '2024-12-11', '2024-12-20', 'FINISHED'),
  (@s_2024h2, '등급산정',   4, 'GRADING',      '2024-12-21', '2024-12-28', 'FINISHED'),
  (@s_2024h2, '결과확정',   5, 'FINALIZATION', '2024-12-29', '2024-12-31', 'FINISHED'),
  -- 2025H1
  (@s_2025h1, '목표등록',   1, 'GOAL_ENTRY',   '2025-01-01', '2025-01-15', 'FINISHED'),
  (@s_2025h1, '자기평가',   2, 'EVALUATION',   '2025-06-01', '2025-06-10', 'FINISHED'),
  (@s_2025h1, '상위자평가', 3, 'EVALUATION',   '2025-06-11', '2025-06-20', 'FINISHED'),
  (@s_2025h1, '등급산정',   4, 'GRADING',      '2025-06-21', '2025-06-28', 'FINISHED'),
  (@s_2025h1, '결과확정',   5, 'FINALIZATION', '2025-06-29', '2025-06-30', 'FINISHED'),
  -- 2025H2
  (@s_2025h2, '목표등록',   1, 'GOAL_ENTRY',   '2025-07-01', '2025-07-15', 'FINISHED'),
  (@s_2025h2, '자기평가',   2, 'EVALUATION',   '2025-12-01', '2025-12-10', 'FINISHED'),
  (@s_2025h2, '상위자평가', 3, 'EVALUATION',   '2025-12-11', '2025-12-20', 'FINISHED'),
  (@s_2025h2, '등급산정',   4, 'GRADING',      '2025-12-21', '2025-12-28', 'FINISHED'),
  (@s_2025h2, '결과확정',   5, 'FINALIZATION', '2025-12-29', '2025-12-31', 'FINISHED'),
  -- 2026H1 (OPEN, 1~3 FINISHED, 4 IN_PROGRESS, 5 WAITING)
  (@s_2026h1, '목표등록',   1, 'GOAL_ENTRY',   '2026-01-01', '2026-01-15', 'FINISHED'),
  (@s_2026h1, '자기평가',   2, 'EVALUATION',   '2026-06-01', '2026-06-10', 'FINISHED'),
  (@s_2026h1, '상위자평가', 3, 'EVALUATION',   '2026-06-11', '2026-06-20', 'FINISHED'),
  (@s_2026h1, '등급산정',   4, 'GRADING',      '2026-06-21', '2026-06-28', 'IN_PROGRESS'),
  (@s_2026h1, '결과확정',   5, 'FINALIZATION', '2026-06-29', '2026-06-30', 'WAITING'),
  -- 2026H2 (DRAFT, 모두 WAITING)
  (@s_2026h2, '목표등록',   1, 'GOAL_ENTRY',   '2026-07-01', '2026-07-15', 'WAITING'),
  (@s_2026h2, '자기평가',   2, 'EVALUATION',   '2026-12-01', '2026-12-10', 'WAITING'),
  (@s_2026h2, '상위자평가', 3, 'EVALUATION',   '2026-12-11', '2026-12-20', 'WAITING'),
  (@s_2026h2, '등급산정',   4, 'GRADING',      '2026-12-21', '2026-12-28', 'WAITING'),
  (@s_2026h2, '결과확정',   5, 'FINALIZATION', '2026-12-29', '2026-12-31', 'WAITING');

-- =====================================================================
-- STEP 5. Goal — 모든 ACTIVE 사원 × 4시즌 (DRAFT 시즌 제외)
--   ▷ KPI 4~5개 (사원당, 직급 매칭 우선 + NULL 매칭 차순) — 가중치 합 100
--      매칭 가능 KPI 부족하면 가능한 만큼 (최소 3개 보장 — 매트릭스 검증됨)
--   ▷ OKR 1~3개 (사원당, emp_id % 3 + 1 개)
--   참여 조건: emp_hire_date < season.start_date
--   상태: 모두 APPROVED (사원 단위 일괄 승인)
-- =====================================================================

-- 5-1. KPI Goal — 사원별 ROW_NUMBER 매칭, rn ≤ (4 or 5), weight는 INSERT 후 UPDATE 로 균등 분배
--   PARTITION BY emp_id → 사원당 KPI 정확히 N개 매칭 (직급 매칭 KPI 우선)
--   weight 임시 100 박은 후 STEP 5-1c 에서 100 / 사원별 KPI 수 로 보정
INSERT INTO goal (emp_id, season_id, kpi_id, goal_type, category, title, description,
                  target_value, target_unit, kpi_direction, weight, approval_status, submitted_at)
SELECT
  k.emp_id,
  s.season_id,
  k.kpi_id,
  'KPI',
  k_cat.option_value,
  k.name,
  k.description,
  CASE k.direction
    WHEN 'UP'   THEN ROUND(k.baseline * 1.10, 2)
    WHEN 'DOWN' THEN ROUND(GREATEST(k.baseline * 0.90, 0.01), 2)
    ELSE              k.baseline
  END,
  k_unit.option_value,
  k.direction,
  100,                                           -- 임시값 (5-1c 에서 보정)
  'APPROVED',
  DATE_ADD(s.start_date, INTERVAL 7 DAY)
FROM (
  SELECT
    e.emp_id, e.emp_hire_date,
    kt.kpi_id, kt.name, kt.description, kt.direction, kt.baseline,
    kt.category_option_id, kt.unit_option_id,
    ROW_NUMBER() OVER (
      PARTITION BY e.emp_id
      ORDER BY
        CASE WHEN kt.grade_id IS NOT NULL THEN 0 ELSE 1 END,  -- 직급 매칭 우선
        kt.kpi_id
    ) AS rn,
    -- 사원별 KPI 수: emp_id % 2 = 0 → 4개, 그 외 → 5개
    CASE WHEN e.emp_id % 2 = 0 THEN 4 ELSE 5 END AS target_kpi_count
  FROM employee e
  JOIN kpi_template kt
    ON kt.department_id = e.dept_id
    AND (kt.grade_id IS NULL OR kt.grade_id = e.grade_id)
    AND kt.is_active = true
  WHERE e.company_id = @cid
    AND e.emp_status = 'ACTIVE'
    AND e.title_id NOT IN (@t_head, @t_ceo)                                  -- 본부장(임원) 제외
) k
JOIN kpi_option k_cat  ON k_cat.option_id  = k.category_option_id
JOIN kpi_option k_unit ON k_unit.option_id = k.unit_option_id
CROSS JOIN season s
WHERE k.rn <= k.target_kpi_count
  AND s.company_id = @cid
  AND s.season_id IN (@s_2024h1, @s_2024h2, @s_2025h1, @s_2025h2, @s_2026h1)
  AND k.emp_hire_date < s.start_date;

-- 5-1c. KPI weight 다이나믹 분배 — emp_id % 5 패턴 × KPI 순번(rn) 으로 차등 부여
--   N=4: 5가지 패턴 (40/30/20/10, 35/30/20/15, 45/25/20/10, 30/30/25/15, 50/20/20/10)
--   N=5: 5가지 패턴 (30/25/20/15/10, 35/25/20/10/10, 25/25/20/15/15, 40/20/20/10/10, 30/30/15/15/10)
--   각 사원 시즌 내 합계는 100 보장, 최소 가중치 10 보장
--   N≠4,5 (3개 이하 — 매트릭스 부족 케이스) 는 fallback 으로 균등 분배
UPDATE goal g
JOIN (
  SELECT
    goal_id, emp_id, season_id,
    ROW_NUMBER() OVER (PARTITION BY emp_id, season_id ORDER BY goal_id) AS rn,
    COUNT(*) OVER (PARTITION BY emp_id, season_id) AS cnt
  FROM goal WHERE goal_type = 'KPI'
) ranked ON ranked.goal_id = g.goal_id
SET g.weight = CASE
  -- N=4 패턴
  WHEN ranked.cnt = 4 AND g.emp_id % 5 = 0 THEN ELT(ranked.rn, 40, 30, 20, 10)
  WHEN ranked.cnt = 4 AND g.emp_id % 5 = 1 THEN ELT(ranked.rn, 35, 30, 20, 15)
  WHEN ranked.cnt = 4 AND g.emp_id % 5 = 2 THEN ELT(ranked.rn, 45, 25, 20, 10)
  WHEN ranked.cnt = 4 AND g.emp_id % 5 = 3 THEN ELT(ranked.rn, 30, 30, 25, 15)
  WHEN ranked.cnt = 4 AND g.emp_id % 5 = 4 THEN ELT(ranked.rn, 50, 20, 20, 10)
  -- N=5 패턴
  WHEN ranked.cnt = 5 AND g.emp_id % 5 = 0 THEN ELT(ranked.rn, 30, 25, 20, 15, 10)
  WHEN ranked.cnt = 5 AND g.emp_id % 5 = 1 THEN ELT(ranked.rn, 35, 25, 20, 10, 10)
  WHEN ranked.cnt = 5 AND g.emp_id % 5 = 2 THEN ELT(ranked.rn, 25, 25, 20, 15, 15)
  WHEN ranked.cnt = 5 AND g.emp_id % 5 = 3 THEN ELT(ranked.rn, 40, 20, 20, 10, 10)
  WHEN ranked.cnt = 5 AND g.emp_id % 5 = 4 THEN ELT(ranked.rn, 30, 30, 15, 15, 10)
  -- fallback (cnt 가 3 이하)
  ELSE FLOOR(100 / ranked.cnt)
END
WHERE g.goal_type = 'KPI';

-- fallback 잔여 분배 (cnt ≤ 3 인 경우만 해당 — 패턴은 이미 합 100 보장)
UPDATE goal g
JOIN (
  SELECT emp_id, season_id, MAX(goal_id) AS last_goal_id, 100 - SUM(weight) AS remainder
  FROM goal WHERE goal_type = 'KPI'
  GROUP BY emp_id, season_id
  HAVING remainder > 0
) r ON r.last_goal_id = g.goal_id
SET g.weight = g.weight + r.remainder;

-- 5-2. OKR Goal — 사원당 1~3개 (emp_id % 3 + 1)
INSERT INTO goal (emp_id, season_id, kpi_id, goal_type, category, title, description,
                  target_value, target_unit, kpi_direction, weight, approval_status, submitted_at)
SELECT
  e.emp_id,
  s.season_id,
  NULL,
  'OKR',
  ELT(1 + ((e.emp_id + okr_n.n) % 5), '업무성과', '프로젝트', '고객만족', '품질', '효율성'),
  ELT(1 + ((e.emp_id + okr_n.n) % 6),
      '자기개발 학습 미션',
      '사내 스터디 운영',
      '업무 자동화 도구 도입',
      '신규 프로세스 제안',
      '협업 문화 개선',
      '외부 강의·세미나 참여'),
  CONCAT('시즌 자기개발 목표 #', okr_n.n, ' — 본인 주도 학습/개선 활동'),
  NULL, NULL, NULL,
  NULL,
  'APPROVED',
  DATE_ADD(s.start_date, INTERVAL 7 DAY)
FROM employee e
CROSS JOIN season s
CROSS JOIN (SELECT 1 AS n UNION SELECT 2 UNION SELECT 3) okr_n
WHERE e.company_id = @cid
  AND e.emp_status = 'ACTIVE'
  AND e.title_id NOT IN (@t_head, @t_ceo)                                    -- 본부장(임원) 제외
  AND s.company_id = @cid
  AND s.season_id IN (@s_2024h1, @s_2024h2, @s_2025h1, @s_2025h2, @s_2026h1)
  AND e.emp_hire_date < s.start_date
  AND okr_n.n <= ((e.emp_id % 3) + 1);   -- 사원당 OKR 1~3개

-- =====================================================================
-- STEP 6. SelfEvaluation
--   actual_value:
--     - KPI UP   → target × (0.7~1.3) — emp_id 기반 분산
--     - KPI DOWN → target × (1.3~0.7) 역방향 (낮을수록 좋음)
--     - KPI MAINTAIN → target ± 5%
--   achievement_level: OKR — emp_id % 5 (EXCELLENT/GOOD/AVERAGE/POOR/INADEQUATE)
--   approval_status: 기본 APPROVED, 일부(emp_id%25=0 + goal_id%3=0)만 REJECTED
-- =====================================================================
INSERT INTO self_evaluation (goal_id, actual_value, achievement_level, achievement_detail, evidence, approval_status, submitted_at)
SELECT
  g.goal_id,
  CASE WHEN g.goal_type = 'KPI' THEN
    CASE g.kpi_direction
      WHEN 'UP'       THEN ROUND(g.target_value * (0.70 + ((g.emp_id + g.kpi_id) % 60) / 100.0), 2)
      WHEN 'DOWN'     THEN ROUND(GREATEST(g.target_value * (1.30 - ((g.emp_id + g.kpi_id) % 60) / 100.0), 0.01), 2)
      WHEN 'MAINTAIN' THEN ROUND(g.target_value * (0.95 + ((g.emp_id + g.kpi_id) % 10) / 100.0), 2)
    END
  ELSE NULL END AS actual_value,
  CASE WHEN g.goal_type = 'OKR' THEN
    ELT(1 + (g.emp_id % 5), 'EXCELLENT', 'GOOD', 'AVERAGE', 'POOR', 'INADEQUATE')
  ELSE NULL END AS achievement_level,
  CONCAT(g.title, ' — 시즌 달성 상세 내역. 정량/정성 결과 기록.'),
  '근거자료 첨부 완료',
  CASE
    WHEN (g.emp_id % 25 = 0 AND g.goal_id % 3 = 0) THEN 'REJECTED'
    ELSE 'APPROVED'
  END,
  DATE_ADD(s.end_date, INTERVAL -10 DAY)
FROM goal g
JOIN season s ON s.season_id = g.season_id
WHERE g.approval_status = 'APPROVED'
  AND s.company_id = @cid
  AND s.season_id IN (@s_2024h1, @s_2024h2, @s_2025h1, @s_2025h2, @s_2026h1);

UPDATE self_evaluation
SET reject_reason = '근거 자료 보완 필요. 정량 데이터 추가 첨부 부탁드립니다.'
WHERE approval_status = 'REJECTED' AND reject_reason IS NULL;

-- =====================================================================
-- STEP 7. ManagerEvaluation — 사원당 1건 (시즌별)
--   evaluator_id: 부서장(부서별 최고 직급)
--     - 부서장 본인 → 대표가 평가
--     - 대표(admin) 본인 → evaluator NULL (최상위, 자기평가만 인정)
--   본부장(T-HEAD = 임원) 제외
-- =====================================================================
-- 7-1. CLOSED 시즌 (grade_label 은 EvalGrade 에 직접 박혀 있어 보정화면 영향 없음)
INSERT INTO manager_evaluation (employee_id, evaluator_id, season_id, grade_label, comment, feedback, submitted_at)
SELECT
  e.emp_id,
  CASE
    WHEN e.emp_id = de.evaluator_emp_id THEN de.evaluator_for_evaluator
    ELSE de.evaluator_emp_id
  END AS evaluator_id,
  s.season_id,
  ELT(1 + ((e.emp_id + s.season_id) % 10), 'S','A','A','B','B','B','B','C','C','D'),
  '시즌 종합 평가 의견 — 목표 달성 수준 및 협업·태도 종합',
  '다음 시즌 성장 포인트 — 강점 유지, 약점 보완 방향 제시',
  DATE_ADD(s.end_date, INTERVAL -5 DAY)
FROM employee e
LEFT JOIN tmp_dept_evaluator de ON de.dept_id = e.dept_id
CROSS JOIN season s
WHERE e.company_id = @cid
  AND e.emp_status = 'ACTIVE'
  AND e.title_id NOT IN (@t_head, @t_ceo)
  AND e.emp_hire_date < s.start_date
  AND s.season_id IN (@s_2024h1, @s_2024h2, @s_2025h1, @s_2025h2);

-- 7-2. OPEN 시즌 (2026H1) — ROW_NUMBER 기반 정확한 분포 보장
--   103명 기준: S=11(+1) A=22(+1) B=40(-1) C=21(=) D=9(-1)
--   ROUND(total_cnt * 비율) 로 인원 변동 시에도 비율 유지
INSERT INTO manager_evaluation (employee_id, evaluator_id, season_id, grade_label, comment, feedback, submitted_at)
SELECT
  sub.emp_id,
  sub.evaluator_id,
  @s_2026h1,
  CASE
    WHEN sub.rn <= ROUND(sub.total_cnt * 0.1068) THEN 'S'
    WHEN sub.rn <= ROUND(sub.total_cnt * 0.3204) THEN 'A'
    WHEN sub.rn <= ROUND(sub.total_cnt * 0.7087) THEN 'B'
    WHEN sub.rn <= ROUND(sub.total_cnt * 0.9126) THEN 'C'
    ELSE 'D'
  END,
  '시즌 종합 평가 의견 — 목표 달성 수준 및 협업·태도 종합',
  '다음 시즌 성장 포인트 — 강점 유지, 약점 보완 방향 제시',
  DATE_ADD((SELECT end_date FROM season WHERE season_id = @s_2026h1), INTERVAL -5 DAY)
FROM (
  SELECT
    e.emp_id,
    CASE
      WHEN e.emp_id = de.evaluator_emp_id THEN de.evaluator_for_evaluator
      ELSE de.evaluator_emp_id
    END AS evaluator_id,
    ROW_NUMBER() OVER (ORDER BY (e.emp_id * 7 + 13) % 997) AS rn,
    COUNT(*)     OVER ()                                    AS total_cnt
  FROM employee e
  LEFT JOIN tmp_dept_evaluator de ON de.dept_id = e.dept_id
  WHERE e.company_id = @cid
    AND e.emp_status = 'ACTIVE'
    AND e.title_id NOT IN (@t_head, @t_ceo)
    AND e.emp_hire_date < '2026-01-01'
) sub;

-- =====================================================================
-- STEP 8. EvalGrade
--   CLOSED 3시즌: locked + 점수/등급 채움
--     self_score = 60 + ((emp_id*7 + season_id*3) % 40)  → 60~99 분산
--     manager_score = grade 매핑 (S=95, A=85, B=75, C=65, D=50)
--     manager_score_adjusted = manager_score + ((emp_id*11 + season_id) % 7 - 3)  → ±3 (Z-score 모방)
--     total_score = self*0.3 + manager_adj*0.7
--     bias_adjusted_score = total_score + ((emp_id*13) % 5 - 2)  → ±2
--     auto_grade = final_grade = manager grade
--   OPEN 1시즌 (2026H1): snapshot only (점수/등급 NULL → 자동재산정 대기)
-- =====================================================================

-- 8-1. CLOSED 시즌 (locked)
INSERT INTO eval_grade
  (emp_id, season_id, version, self_score, raw_self_score, manager_score, manager_score_adjusted,
   total_score, weighted_score, bias_adjusted_score,
   auto_grade, final_grade, is_calibrated, locked_at,
   dept_id_snapshot, dept_name_snapshot, position_snapshot,
   evaluator_id_snapshot, evaluator_name_snapshot)
SELECT
  e.emp_id,
  s.season_id,
  0,                                             -- version (낙관적 락 초기값)
  -- self_score: 60~99 분산
  60 + ((e.emp_id * 7 + s.season_id * 3) % 40),
  60 + ((e.emp_id * 7 + s.season_id * 3) % 40),
  -- manager_score: 등급 매핑
  CASE ELT(1 + ((e.emp_id + s.season_id) % 10), 'S','A','A','B','B','B','B','C','C','D')
    WHEN 'S' THEN 95 WHEN 'A' THEN 85 WHEN 'B' THEN 75 WHEN 'C' THEN 65 WHEN 'D' THEN 50
  END,
  -- manager_score_adjusted: 일반 케이스는 ±3 변동(Z-score 모방), 보정 스킵 케이스는 원점수 유지
  --   ▷ 감사실(@d_audit): 소규모 팀 (3명 < minTeamSize=5) → 보정 스킵
  --   ▷ emp_id % 11 = 0: 팀 평균과 일치 가정 (z=0 시연용) → 보정 결과 0
  CASE ELT(1 + ((e.emp_id + s.season_id) % 10), 'S','A','A','B','B','B','B','C','C','D')
    WHEN 'S' THEN 95 WHEN 'A' THEN 85 WHEN 'B' THEN 75 WHEN 'C' THEN 65 WHEN 'D' THEN 50
  END + (CASE WHEN e.dept_id = @d_audit OR e.emp_id % 11 = 0 THEN 0 ELSE ((e.emp_id * 11 + s.season_id) % 7) - 3 END),
  -- total_score: self*0.3 + mgr_adj*0.7
  ROUND(
    (60 + ((e.emp_id * 7 + s.season_id * 3) % 40)) * 0.3 +
    (CASE ELT(1 + ((e.emp_id + s.season_id) % 10), 'S','A','A','B','B','B','B','C','C','D')
       WHEN 'S' THEN 95 WHEN 'A' THEN 85 WHEN 'B' THEN 75 WHEN 'C' THEN 65 WHEN 'D' THEN 50
     END + (CASE WHEN e.dept_id = @d_audit OR e.emp_id % 11 = 0 THEN 0 ELSE ((e.emp_id * 11 + s.season_id) % 7) - 3 END)) * 0.7
  , 2),
  -- weighted_score: total_score 와 동일
  ROUND(
    (60 + ((e.emp_id * 7 + s.season_id * 3) % 40)) * 0.3 +
    (CASE ELT(1 + ((e.emp_id + s.season_id) % 10), 'S','A','A','B','B','B','B','C','C','D')
       WHEN 'S' THEN 95 WHEN 'A' THEN 85 WHEN 'B' THEN 75 WHEN 'C' THEN 65 WHEN 'D' THEN 50
     END + (CASE WHEN e.dept_id = @d_audit OR e.emp_id % 11 = 0 THEN 0 ELSE ((e.emp_id * 11 + s.season_id) % 7) - 3 END)) * 0.7
  , 2),
  -- bias_adjusted_score: total + ±2
  ROUND(
    (60 + ((e.emp_id * 7 + s.season_id * 3) % 40)) * 0.3 +
    (CASE ELT(1 + ((e.emp_id + s.season_id) % 10), 'S','A','A','B','B','B','B','C','C','D')
       WHEN 'S' THEN 95 WHEN 'A' THEN 85 WHEN 'B' THEN 75 WHEN 'C' THEN 65 WHEN 'D' THEN 50
     END + (CASE WHEN e.dept_id = @d_audit OR e.emp_id % 11 = 0 THEN 0 ELSE ((e.emp_id * 11 + s.season_id) % 7) - 3 END)) * 0.7
    + (((e.emp_id * 13) % 5) - 2)
  , 2),
  ELT(1 + ((e.emp_id + s.season_id) % 10), 'S','A','A','B','B','B','B','C','C','D'),
  ELT(1 + ((e.emp_id + s.season_id) % 10), 'S','A','A','B','B','B','B','C','C','D'),
  false,
  s.finalized_at,
  e.dept_id,
  d.dept_name,
  CASE e.grade_id
    WHEN @g6 THEN '이사'
    WHEN @g5 THEN '부장'
    WHEN @g4 THEN '차장'
    WHEN @g3 THEN '과장'
    WHEN @g2 THEN '대리'
    WHEN @g1 THEN '사원'
    ELSE '미배정'
  END,
  CASE
    WHEN e.emp_id = de.evaluator_emp_id THEN de.evaluator_for_evaluator
    ELSE de.evaluator_emp_id
  END,
  (SELECT emp_name FROM employee
    WHERE emp_id = (CASE
      WHEN e.emp_id = de.evaluator_emp_id THEN de.evaluator_for_evaluator
      ELSE de.evaluator_emp_id
    END))
FROM employee e
JOIN department d ON d.dept_id = e.dept_id
LEFT JOIN tmp_dept_evaluator de ON de.dept_id = e.dept_id
CROSS JOIN season s
WHERE e.company_id = @cid
  AND e.emp_status = 'ACTIVE'
  AND e.title_id NOT IN (@t_head, @t_ceo)
  AND e.emp_hire_date < s.start_date
  AND s.season_id IN (@s_2024h1, @s_2024h2, @s_2025h1, @s_2025h2);

-- 8-1c. self_score 재계산 — KPI 가중치 × 달성률 가중평균 (cap 120, score 0~120)
--   기존 8-1 의 self_score(60+hash) 는 임시값. 가중치가 다이나믹해진 만큼 결과도 가중치를 반영해야 함.
--   per-goal 달성률:
--     UP       → actual / target × 100  (높을수록 좋음)
--     DOWN     → target / actual × 100  (낮을수록 좋음)
--     MAINTAIN → 100 - |1 - actual/target| × 100  (목표값 근접할수록 100)
--   상한 120 (kpiScoring.cap), 하한 0
--   final self_score = SUM(score_per_goal × weight) / 100
--   total/weighted/bias 도 self_score 를 기반으로 같이 갱신
UPDATE eval_grade eg
JOIN (
  SELECT
    g.emp_id, g.season_id,
    ROUND(SUM(
      LEAST(120, GREATEST(0,
        CASE g.kpi_direction
          WHEN 'UP'       THEN (se.actual_value / NULLIF(g.target_value, 0)) * 100
          WHEN 'DOWN'     THEN (g.target_value / NULLIF(se.actual_value, 0.01)) * 100
          WHEN 'MAINTAIN' THEN 100 - ABS(1 - se.actual_value / NULLIF(g.target_value, 0)) * 100
          ELSE 75
        END
      )) * g.weight / 100
    ), 2) AS computed_self_score
  FROM goal g
  JOIN self_evaluation se ON se.goal_id = g.goal_id
  WHERE g.goal_type = 'KPI'
    AND se.actual_value IS NOT NULL
  GROUP BY g.emp_id, g.season_id
) c ON c.emp_id = eg.emp_id AND c.season_id = eg.season_id
SET
  eg.self_score          = c.computed_self_score,
  eg.raw_self_score      = c.computed_self_score,
  eg.total_score         = ROUND(c.computed_self_score * 0.3 + eg.manager_score_adjusted * 0.7, 2),
  eg.weighted_score      = ROUND(c.computed_self_score * 0.3 + eg.manager_score_adjusted * 0.7, 2),
  -- bias_adjusted_score: 보정 스킵 케이스(감사실/z=0 demo)는 ±2 노이즈 없이 total 그대로
  eg.bias_adjusted_score = ROUND(c.computed_self_score * 0.3 + eg.manager_score_adjusted * 0.7
                                 + CASE
                                     WHEN eg.dept_id_snapshot = @d_audit OR eg.emp_id % 11 = 0 THEN 0
                                     ELSE ((eg.emp_id * 13) % 5) - 2
                                   END, 2)
WHERE eg.season_id IN (@s_2024h1, @s_2024h2, @s_2025h1, @s_2025h2);

-- 8-2. OPEN 시즌 (snapshot only — 자동산정 단계에서 점수/등급 채워짐)
INSERT INTO eval_grade
  (emp_id, season_id, version, is_calibrated,
   dept_id_snapshot, dept_name_snapshot, position_snapshot,
   evaluator_id_snapshot, evaluator_name_snapshot)
SELECT
  e.emp_id,
  @s_2026h1,
  0,
  false,
  e.dept_id,
  d.dept_name,
  CASE e.grade_id
    WHEN @g6 THEN '이사'
    WHEN @g5 THEN '부장'
    WHEN @g4 THEN '차장'
    WHEN @g3 THEN '과장'
    WHEN @g2 THEN '대리'
    WHEN @g1 THEN '사원'
    ELSE '미배정'
  END,
  CASE
    WHEN e.emp_id = de.evaluator_emp_id THEN de.evaluator_for_evaluator
    ELSE de.evaluator_emp_id
  END,
  (SELECT emp_name FROM employee
    WHERE emp_id = (CASE
      WHEN e.emp_id = de.evaluator_emp_id THEN de.evaluator_for_evaluator
      ELSE de.evaluator_emp_id
    END))
FROM employee e
JOIN department d ON d.dept_id = e.dept_id
LEFT JOIN tmp_dept_evaluator de ON de.dept_id = e.dept_id
WHERE e.company_id = @cid
  AND e.emp_status = 'ACTIVE'
  AND e.title_id NOT IN (@t_head, @t_ceo)
  AND e.emp_hire_date < '2026-01-01';


-- =====================================================================
-- STEP 9. Calibration — CLOSED 시즌 일부 사원에 등급 보정 이력 추가
--   대상: emp_id % 17 = 0 사원 중 auto_grade ∈ {C, D}
--     C → B 로 한 단계 상향, D → C 로 한 단계 상향
--   사유: 평가조정회의 결과 (재평가 반영)
--   actor_id: 대표(@e_ceo) 가 보정 수행
--   보정 후 final_grade 와 is_calibrated 도 업데이트
-- =====================================================================
INSERT INTO calibration (grade_id, from_grade, to_grade, reason, actor_id, created_at, updated_at)
SELECT
  eg.grade_id,
  eg.auto_grade,
  CASE eg.auto_grade WHEN 'D' THEN 'C' WHEN 'C' THEN 'B' END,
  CONCAT('평가조정회의 결과 — ',
         CASE eg.auto_grade
           WHEN 'D' THEN '기여도 재평가 반영, 분기 후반 회복세 인정 (D→C)'
           WHEN 'C' THEN '동료 피드백·프로젝트 기여 종합 평가 후 상향 조정 (C→B)'
         END),
  @e_ceo,
  s.finalized_at,
  s.finalized_at
FROM eval_grade eg
JOIN season s ON s.season_id = eg.season_id
WHERE s.company_id = @cid
  AND s.status = 'CLOSED'
  AND eg.emp_id % 17 = 0
  AND eg.auto_grade IN ('C', 'D');

-- 보정 반영: final_grade 업데이트 + is_calibrated true
UPDATE eval_grade eg
JOIN calibration c ON c.grade_id = eg.grade_id
SET eg.final_grade = c.to_grade,
    eg.is_calibrated = true;

-- 임시 테이블 정리
DROP TEMPORARY TABLE IF EXISTS tmp_dept_evaluator;
DROP TEMPORARY TABLE IF EXISTS tmp_dept_ranked;

-- =====================================================================
-- 검증 쿼리
-- =====================================================================
SELECT '=== 평가 데이터 합산 ===' AS section;
SELECT 'kpi_option',         COUNT(*) FROM kpi_option         WHERE company_id = @cid UNION ALL
SELECT 'kpi_template',       COUNT(*) FROM kpi_template kt JOIN department d ON d.dept_id = kt.department_id WHERE d.company_id = @cid UNION ALL
SELECT 'emp_evaluator_global',COUNT(*) FROM emp_evaluator_global WHERE company_id = @cid UNION ALL
SELECT 'season',             COUNT(*) FROM season             WHERE company_id = @cid UNION ALL
SELECT 'stage',              COUNT(*) FROM stage st JOIN season s ON s.season_id = st.season_id WHERE s.company_id = @cid UNION ALL
SELECT 'goal',               COUNT(*) FROM goal g JOIN season s ON s.season_id = g.season_id WHERE s.company_id = @cid UNION ALL
SELECT 'self_evaluation',    COUNT(*) FROM self_evaluation se JOIN goal g ON g.goal_id = se.goal_id JOIN season s ON s.season_id = g.season_id WHERE s.company_id = @cid UNION ALL
SELECT 'manager_evaluation', COUNT(*) FROM manager_evaluation me JOIN season s ON s.season_id = me.season_id WHERE s.company_id = @cid UNION ALL
SELECT 'eval_grade',         COUNT(*) FROM eval_grade eg JOIN season s ON s.season_id = eg.season_id WHERE s.company_id = @cid UNION ALL
SELECT 'calibration',        COUNT(*) FROM calibration c JOIN eval_grade eg ON eg.grade_id = c.grade_id JOIN season s ON s.season_id = eg.season_id WHERE s.company_id = @cid;

SELECT '=== 시즌별 평가 분포 ===' AS section;
SELECT
  s.name, s.status,
  (SELECT COUNT(*) FROM goal               WHERE season_id = s.season_id) AS goal_cnt,
  (SELECT COUNT(*) FROM self_evaluation se JOIN goal g ON g.goal_id = se.goal_id WHERE g.season_id = s.season_id) AS self_cnt,
  (SELECT COUNT(*) FROM manager_evaluation WHERE season_id = s.season_id) AS mgr_cnt,
  (SELECT COUNT(*) FROM eval_grade         WHERE season_id = s.season_id) AS grade_cnt,
  (SELECT COUNT(*) FROM eval_grade         WHERE season_id = s.season_id AND final_grade IS NOT NULL) AS finalized_cnt
FROM season s
WHERE s.company_id = @cid
ORDER BY s.start_date;

SELECT '=== 부서별 평가자 매핑 ===' AS section;
SELECT d.dept_name AS '부서',
       evaluator.emp_name AS '평가자',
       evaluator_grade.grade_name AS '평가자 직급',
       (SELECT COUNT(*) FROM emp_evaluator_global eg WHERE eg.evaluator_emp_id = evaluator.emp_id) AS '담당 사원 수'
FROM department d
LEFT JOIN employee evaluator ON evaluator.emp_id = (
  SELECT MIN(e.emp_id) FROM employee e JOIN grade g ON g.grade_id = e.grade_id
  WHERE e.dept_id = d.dept_id AND e.emp_status = 'ACTIVE'
    AND g.grade_order = (SELECT MAX(g2.grade_order) FROM employee e2 JOIN grade g2 ON g2.grade_id = e2.grade_id
                         WHERE e2.dept_id = d.dept_id AND e2.emp_status = 'ACTIVE')
)
LEFT JOIN grade evaluator_grade ON evaluator_grade.grade_id = evaluator.grade_id
WHERE d.company_id = @cid AND d.dept_code != 'DEFAULT'
ORDER BY d.dept_id;

SELECT '=== 등급 분포 (CLOSED 시즌 합산) ===' AS section;
SELECT eg.final_grade, COUNT(*) AS cnt
FROM eval_grade eg
JOIN season s ON s.season_id = eg.season_id
WHERE s.company_id = @cid AND s.status = 'CLOSED' AND eg.final_grade IS NOT NULL
GROUP BY eg.final_grade
ORDER BY FIELD(eg.final_grade, 'S','A','B','C','D');


-- =====================================================================
-- [화면용] 개발팀 전원 동점 — 보정 참고사항 zeroStdDevTeams 시연
-- ---------------------------------------------------------------------
-- 개발팀 박제된 EvalGrade 의 manager_score 를 동일값으로 고정.
-- team_std_dev=0 으로 박아 백엔드 getCalibrationReview 에서 zeroStdDevTeams 에 잡히게.
-- =====================================================================
UPDATE eval_grade eg
SET manager_score          = 80,
    manager_score_adjusted = 80,
    team_std_dev           = 0
WHERE dept_id_snapshot = (SELECT dept_id FROM department WHERE company_id=@cid AND dept_code='DEV');


COMMIT;
