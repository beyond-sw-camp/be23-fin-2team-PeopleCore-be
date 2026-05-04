use peoplecore;

-- =====================================================================
-- HR Service 더미 마스터 데이터 (Department / Grade / Title / WorkGroup)
-- ---------------------------------------------------------------------
-- 대상 DB : MySQL 8.x  (UUID 컬럼은 BINARY(16) 매핑)
--
-- [선행 조건]
--   회사가 서비스 레이어를 통해 먼저 생성되어 있어야 함.
--   회사 생성 시 자동으로 다음이 깔리는 것을 확인:
--     - department 1건 ('미배정' / DEFAULT)  → 본 스크립트는 건드리지 않음
--     - grade 1건                          → 그대로 둠
--     - title 1건                          → 그대로 둠
--     - work_group 1건 (기본 9-18)          → 그대로 둠
--     - insurance_job_types 19건 (한국 표준 업종) → 본 스크립트는 추가 INSERT 안 함
--   본 스크립트는 명시 ID 없이 AUTO_INCREMENT 에 맡겨 자동 데이터와 충돌 회피.
--
--   ▼ 회사명 약속 ▼
--     team 공통 더미 회사명: 'peoplecore'
--
-- [실행 방법]
--   1) 회사 'peoplecore' 를 회사 생성 API/UI 로 먼저 만들기
--   2) $ mysql -u <user> -p peoplecore < 01_hr_master_data.sql
--      또는 DataGrip/DBeaver 에서 파일 통째로 Run
--   3) 이어서 02_hr_employees.sql 실행
--
-- [검증]
--   스크립트 끝에 SELECT 검증 쿼리 포함.
-- =====================================================================

-- ▼ 회사 생성 시 입력한 회사명. 디폴트 그대로 사용 시 수정 불필요 ▼
SET @company_name := 'peoplecore';

-- 회사명으로 UUID 자동 조회 (BINARY(16) 그대로 반환)
SET @cid := (SELECT company_id FROM company WHERE company_name = @company_name);

-- 회사 못 찾으면 @cid NULL → 아래 INSERT FK 위반으로 즉시 실패
SELECT
  IFNULL(BIN_TO_UUID(@cid),
         CONCAT('❌ 회사를 찾을 수 없습니다: ', @company_name,
                ' / 회사 생성 후 다시 실행하세요.')) AS resolved_company;


-- =====================================================================
-- 1) Department  (자동 '미배정' 그대로 두고 평면 7개 추가)
-- ---------------------------------------------------------------------
--   임원실 (EXEC) / 인사팀 (HR) / 재무팀 (FIN) / 개발팀 (DEV) /
--   인프라팀 (INF) / 영업팀 (SALES) / 마케팅팀 (MKT)
-- =====================================================================

INSERT INTO department (company_id, parent_dept_id, dept_name, dept_code, created_at, is_use, sort_order) VALUES
(@cid, NULL, '임원실',   'EXEC',  NOW(), 'Y', 1),
(@cid, NULL, '인사팀',   'HR',    NOW(), 'Y', 2),
(@cid, NULL, '재무팀',   'FIN',   NOW(), 'Y', 3),
(@cid, NULL, '개발팀',   'DEV',   NOW(), 'Y', 4),
(@cid, NULL, '인프라팀', 'INF',   NOW(), 'Y', 5),
(@cid, NULL, '영업팀',   'SALES', NOW(), 'Y', 6),
(@cid, NULL, '마케팅팀', 'MKT',   NOW(), 'Y', 7);
-- =====================================================================
-- 2) Grade  (한국 일반 직급 6단계 추가)
--   사원(G1) → 대리(G2) → 과장(G3) → 차장(G4) → 부장(G5) → 이사(G6)
-- =====================================================================

INSERT INTO grade (company_id, grade_name, grade_code, grade_order) VALUES
(@cid, '사원', 'G1', 1),
(@cid, '대리', 'G2', 2),
(@cid, '과장', 'G3', 3),
(@cid, '차장', 'G4', 4),
(@cid, '부장', 'G5', 5),
(@cid, '이사', 'G6', 6);


-- =====================================================================
-- 3) Title  (직책 4종 추가, 회사 공통 dept_id = NULL)
--   팀원 / 팀장 / 본부장 / 대표
-- =====================================================================

INSERT INTO title (company_id, title_name, title_code, title_order) VALUES
(@cid, '팀원',   'T-MEMBER', 1),
(@cid, '팀장',   'T-LEAD',   2),
(@cid, '본부장', 'T-HEAD',   3),
(@cid, '대표',   'T-CEO',    4);


-- =====================================================================
-- 4) InsuranceJobTypes
-- ---------------------------------------------------------------------
-- ※ 회사 생성 시 한국 산재보험 표준 업종 19종이 자동 시드됨. 추가 INSERT 없음.
--   사원 매핑은 02_hr_employees.sql 에서 다음 4개를 부서별로 사용:
--     '금융/보험업'    → 임원실/인사/재무   (요율 0.0070)
--     'IT/소프트웨어'  → 개발/인프라         (요율 0.0070)
--     '도소매업'      → 영업                (요율 0.0090)
--     '기타 서비스업'  → 마케팅              (요율 0.0090, 광고업 별도 항목 없음)
-- =====================================================================


-- =====================================================================
-- 5) WorkGroup  (자동 기본 9-18 그룹은 그대로, 추가 3종 INSERT)
-- ---------------------------------------------------------------------
-- group_work_day 비트마스크 : 월1+화2+수4+목8+금16=31(월~금) / +토32=63(월~토)
-- group_overtime_recognize  : APPROVAL(승인분만) / ALL(전체 인정)
--
--   WG-FLEX-DEV  : 개발 유연근무 (월~금 10:00-19:00, OT 전체 인정)
--   WG-SALES-SAT : 영업 외근    (월~토 09:00-18:00, OT 승인분만)
--   WG-SHORT     : 단축근무     (월~금 09:00-15:00, 임산부/육아기)
-- =====================================================================

INSERT INTO work_group
  (company_id, group_name, group_code, group_desc,
   group_start_time, group_end_time, group_work_day,
   group_break_start, group_break_end, group_overtime_recognize,
   group_delete_at, group_manager_id, group_manager_name,
   created_at, updated_at)
VALUES
(@cid, '개발 유연근무(10-19)', 'WG-FLEX-DEV',
 '월~금 10:00-19:00, 휴게 12:30~13:30, OT 전체 인정',
 '10:00:00', '19:00:00', 31,
 '12:30:00', '13:30:00', 'ALL',
 NULL, NULL, NULL, NOW(), NOW()),

(@cid, '영업 외근(월~토)', 'WG-SALES-SAT',
 '월~토 09:00-18:00, 외근 잦음, OT 승인분만 인정',
 '09:00:00', '18:00:00', 63,
 '12:00:00', '13:00:00', 'APPROVAL',
 NULL, NULL, NULL, NOW(), NOW()),

(@cid, '단축근무(9-15)', 'WG-SHORT',
 '임산부·육아기 단축근무 09:00-15:00, 휴게 12:00~13:00',
 '09:00:00', '15:00:00', 31,
 '12:00:00', '13:00:00', 'APPROVAL',
 NULL, NULL, NULL, NOW(), NOW());


-- =====================================================================
-- 6) CompanyPaySettings  (회사 급여 지급 설정 — 자동 시드된 행 갱신)
-- ---------------------------------------------------------------------
--   ⚠️ 회사 생성 시 PaySettingsService.initDefault() 가 이미 1행 자동 생성
--      (NEXT 익월 / 25일 / 004 국민). INSERT 하면 중복 → NonUniqueResultException.
--      따라서 UPDATE 로 자동 시드 행을 더미 의도(CURRENT 당월)로 변경.
--   ※ company_id 당 1행. UI 에서 변경 시 update().
-- =====================================================================

UPDATE company_pay_settings
   SET salary_pay_day      = 25,
       salary_pay_last_day = FALSE,
       salary_pay_month    = 'CURRENT',     -- 자동 시드는 NEXT(익월) → 당월 지급으로 변경
       main_bank_code      = '004',
       main_bank_name      = '국민은행'
 WHERE company_id = @cid;


-- =====================================================================
-- 7) RetirementSettings  (회사 퇴직연금 설정 1건)
-- ---------------------------------------------------------------------
--   pension_type = 'DB_DC' (병행) → 사원별로 DB/DC 선택 가능 (가장 풍부한 테스트)
--   운용사: 미래에셋증권
--   회사 운용계좌(pension_account): DB/DB_DC 시에만 의미 있음
--   ※ RetirementSettings 는 BaseTimeEntity 미상속 → created_at/updated_at 컬럼 없음
-- =====================================================================

INSERT INTO retirement_settings
  (company_id, pension_type, pension_provider, pension_account)
VALUES
  (@cid, 'DB_DC', '미래에셋증권', '123-456-789012');


-- =====================================================================
-- [검증 쿼리] 실행 후 카운트 확인 (자동 + 추가)
--   department         : 8  (자동 1 + 추가 7)
--   grade              : 7  (자동 1 + 추가 6)
--   title              : 5  (자동 1 + 추가 4)
--   insurance_job_types: 19 (자동만)
--   work_group         : 4  (자동 1 + 추가 3)
-- =====================================================================
-- SELECT 'department'         AS tbl, COUNT(*) AS cnt FROM department         WHERE company_id = @cid
-- UNION ALL
-- SELECT 'grade',                     COUNT(*)        FROM grade              WHERE company_id = @cid
-- UNION ALL
-- SELECT 'title',                     COUNT(*)        FROM title              WHERE company_id = @cid
-- UNION ALL
-- SELECT 'insurance_job_types',       COUNT(*)        FROM insurance_job_types WHERE company_id = @cid
-- UNION ALL
-- SELECT 'work_group',                COUNT(*)        FROM work_group         WHERE company_id = @cid;
