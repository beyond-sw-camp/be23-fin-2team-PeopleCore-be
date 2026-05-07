-- =====================================================================
-- HR Service 더미 인사발령 데이터
-- ---------------------------------------------------------------------
-- 본 스크립트가 시드하는 발령 이력 (백엔드 HrOrderService.history() 통합 관점):
--   1) PROMOTION (승진)      — hr_order + hr_order_detail(GRADE)        × 다단계
--   2) TRANSFER  (전보)      — hr_order + hr_order_detail(DEPARTMENT)   × 1회/사원
--   3) TITLE_CHANGE (보직변경) — hr_order + hr_order_detail(TITLE)        × 1회/사원
--   4) HIRE  (입사)           — employee.emp_hire_date 에서 백엔드가 자동 합성
--   5) RESIGN (퇴사)          — resign 테이블에서 retire_status='RESIGNED' 만 합성
--
-- 선행 조건:
--   1) 회사 'peoplecore' 생성 완료
--   2) 01_hr_master_data.sql 실행 완료 (Department/Grade/Title)
--   3) 02_hr_employees.sql 실행 완료 (사원 100명, 퇴사 2명 포함)
--
-- 패턴 분기 (PROMOTION, emp_id % 10):
--   0~5 (60%): 통상 4년 주기 정상 진급
--   6~7 (20%): 빠름 3년 주기 (우수자 시뮬)
--   8   (10%): 케이스 A (진급 정체 시뮬) — 통상 4년 주기지만 마지막 한 단계 덜
--   9   (10%): 케이스 B (우수자 정체 시뮬) — 빠름 3년 주기 (마지막 진급 시점이 자연히 일찍)
--
-- TRANSFER (전보) 대상:
--   emp_id % 10 IN (1, 4)  + 입사 3년 이상 + ACTIVE + 본부장/대표 제외
--   이전 부서 ↔ 현재 부서 매핑(고정): HR→DEV, FIN→SALES, DEV→INF, INF→DEV,
--                                   SALES→MKT, MKT→SALES (EXEC 사원은 대상 아님)
--   effective_date = emp_hire_date + 3년
--
-- TITLE_CHANGE (보직변경) 대상:
--   현재 title=T-LEAD (팀장) 6명 — 입사 후 N년차에 T-MEMBER → T-LEAD 보직변경
--   effective_date = emp_hire_date + 5년
--   (T-HEAD 본부장은 외부영입/별도 트랙으로 보고 발령 시드 제외)
--
-- RESIGN (퇴사) 대상:
--   employee.emp_status = 'RESIGNED' 인 사원 (02 시드 기준 2명: emp035, emp076)
--   resign 테이블에 retire_status='RESIGNED' 로 1건씩 시드
--
-- grade_distance = grade_order - @g_min_order (현 직급까지 진급 횟수)
--   ▷ peoplecore (6단계): 사원=0회 / 대리=1회 / 과장=2회 / ... / 이사=5회
--   ▷ DEFAULT 직급(미배정)은 자동 제외
--   ▷ 회사 단계 수가 달라도 grade_order만 보므로 동작 동일
--
-- 제외 대상 (PROMOTION 공통):
--   - 본부장(T-HEAD = 임원실 emp002~004) — 평가/발령 모두 제외 (기존 03 시드와 일관)
--   - emp_status != ACTIVE (퇴사/휴직)
--   - admin(emp001) 본인 — HR_SUPER_ADMIN 은 진급 이력 시드 안 함
--   - DEFAULT 직급 사원 (peoplecore 더미엔 없지만 다른 회사 보호)
--
-- form_snapshot / form_values:
--   form_snapshot = '[]' (NOT NULL 만족용 빈 배열)
--   form_values = JSON 객체 (orderTitle, orderReason)
--
-- 재실행 가능 — 상단 cleanup 블록이 회사 발령 + resign 모두 삭제
-- =====================================================================

USE peoplecore;

SET @company_name := 'peoplecore';
SET @cid := (SELECT company_id FROM company WHERE company_name = @company_name);

-- ▼ 회사별 lookup ▼
SET @e_ceo      := (SELECT emp_id FROM employee WHERE company_id=@cid AND emp_num='EMP-2025-001');
SET @t_head     := (SELECT title_id FROM title WHERE company_id=@cid AND title_code='T-HEAD');
SET @t_lead     := (SELECT title_id FROM title WHERE company_id=@cid AND title_code='T-LEAD');
SET @t_mem      := (SELECT title_id FROM title WHERE company_id=@cid AND title_code='T-MEMBER');

-- 회사별 최저 grade_order (DEFAULT 제외) = 신입 직급 = 진급 0회 기준점
SET @g_min_order := (SELECT MIN(grade_order) FROM grade
                     WHERE company_id=@cid AND grade_code != 'DEFAULT');

SELECT
  IFNULL(BIN_TO_UUID(@cid),
         CONCAT('❌ 회사를 찾을 수 없습니다: ', @company_name)) AS resolved_company,
  @g_min_order AS min_grade_order,
  (SELECT MAX(grade_order) FROM grade
    WHERE company_id=@cid AND grade_code != 'DEFAULT') AS max_grade_order;

-- =====================================================================
-- ★ CLEANUP: 이전 실행 잔재 정리 (회사별)
-- =====================================================================
SET FOREIGN_KEY_CHECKS = 0;
DELETE FROM hr_order_detail WHERE order_id IN (SELECT order_id FROM hr_order WHERE company_id = @cid);
DELETE FROM hr_order WHERE company_id = @cid;
DELETE FROM resign WHERE emp_id IN (SELECT emp_id FROM employee WHERE company_id = @cid);
SET FOREIGN_KEY_CHECKS = 1;

-- =====================================================================
-- STEP 1. PROMOTION (승진) — 패턴별 4회
-- ---------------------------------------------------------------------
-- step 카운터 1~5 (peoplecore 6단계 → 최대 5회 진급)
-- 회사 단계가 더 많으면 step UNION 추가. step.n <= grade_distance 가 자동 컷.
-- =====================================================================

-- ── 1-A. 통상 패턴 (emp_id % 10 IN 0~5) — 4년 주기 ──
INSERT INTO hr_order
  (company_id, emp_id, create_by, effective_date, order_type, is_notified, notified_at,
   status, form_values, form_snapshot, form_version, created_at)
SELECT
  @cid,
  e.emp_id,
  @e_ceo,
  DATE_ADD(e.emp_hire_date, INTERVAL 4 * step.n YEAR),
  'PROMOTION',
  TRUE,
  DATE_ADD(DATE_ADD(e.emp_hire_date, INTERVAL 4 * step.n YEAR), INTERVAL -7 DAY),
  'APPLIED',
  JSON_OBJECT(
    'orderTitle',  CONCAT(YEAR(DATE_ADD(e.emp_hire_date, INTERVAL 4 * step.n YEAR)), '년 정기 인사발령'),
    'orderReason', CONCAT('근속 ', 4 * step.n, '년차 통상 진급')
  ),
  '[]',
  UNIX_TIMESTAMP() * 1000,
  NOW(6)
FROM employee e
JOIN grade g ON g.grade_id = e.grade_id
CROSS JOIN (SELECT 1 AS n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5) step
WHERE e.company_id = @cid
  AND e.emp_status = 'ACTIVE'
  AND e.title_id != @t_head
  AND e.emp_id != @e_ceo
  AND g.grade_code != 'DEFAULT'
  AND e.emp_id % 10 <= 5
  AND step.n <= (g.grade_order - @g_min_order)
  AND DATE_ADD(e.emp_hire_date, INTERVAL 4 * step.n YEAR) <= CURRENT_DATE;

-- ── 1-B. 빠른 진급 (emp_id % 10 IN 6,7) — 3년 주기 ──
INSERT INTO hr_order
  (company_id, emp_id, create_by, effective_date, order_type, is_notified, notified_at,
   status, form_values, form_snapshot, form_version, created_at)
SELECT
  @cid,
  e.emp_id,
  @e_ceo,
  DATE_ADD(e.emp_hire_date, INTERVAL 3 * step.n YEAR),
  'PROMOTION',
  TRUE,
  DATE_ADD(DATE_ADD(e.emp_hire_date, INTERVAL 3 * step.n YEAR), INTERVAL -7 DAY),
  'APPLIED',
  JSON_OBJECT(
    'orderTitle',  CONCAT(YEAR(DATE_ADD(e.emp_hire_date, INTERVAL 3 * step.n YEAR)), '년 정기 인사발령'),
    'orderReason', CONCAT('근속 ', 3 * step.n, '년차 우수자 조기 진급')
  ),
  '[]',
  UNIX_TIMESTAMP() * 1000,
  NOW(6)
FROM employee e
JOIN grade g ON g.grade_id = e.grade_id
CROSS JOIN (SELECT 1 AS n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5) step
WHERE e.company_id = @cid
  AND e.emp_status = 'ACTIVE'
  AND e.title_id != @t_head
  AND e.emp_id != @e_ceo
  AND g.grade_code != 'DEFAULT'
  AND e.emp_id % 10 IN (6, 7)
  AND step.n <= (g.grade_order - @g_min_order)
  AND DATE_ADD(e.emp_hire_date, INTERVAL 3 * step.n YEAR) <= CURRENT_DATE;

-- ── 1-C. 케이스 A (emp_id % 10 = 8) — 통상 4년 주기, 마지막 진급 1회 누락 ──
--   현 직급 도달까지 N번 필요한데 (N-1)번만 진급 시드 → 마지막 진급은 안 일어남
--   해석: 이전 직급에 머무를 수 있었지만 현 직급이라 함. 그러나 발령 이력은 정체 시뮬용.
--   대상: grade_distance >= 2 (최소 한 번은 진급 발생, 마지막 한 단계 덜)
INSERT INTO hr_order
  (company_id, emp_id, create_by, effective_date, order_type, is_notified, notified_at,
   status, form_values, form_snapshot, form_version, created_at)
SELECT
  @cid,
  e.emp_id,
  @e_ceo,
  DATE_ADD(e.emp_hire_date, INTERVAL 4 * step.n YEAR),
  'PROMOTION',
  TRUE,
  DATE_ADD(DATE_ADD(e.emp_hire_date, INTERVAL 4 * step.n YEAR), INTERVAL -7 DAY),
  'APPLIED',
  JSON_OBJECT(
    'orderTitle',  CONCAT(YEAR(DATE_ADD(e.emp_hire_date, INTERVAL 4 * step.n YEAR)), '년 정기 인사발령'),
    'orderReason', CONCAT('근속 ', 4 * step.n, '년차 통상 진급')
  ),
  '[]',
  UNIX_TIMESTAMP() * 1000,
  NOW(6)
FROM employee e
JOIN grade g ON g.grade_id = e.grade_id
CROSS JOIN (SELECT 1 AS n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5) step
WHERE e.company_id = @cid
  AND e.emp_status = 'ACTIVE'
  AND e.title_id != @t_head
  AND e.emp_id != @e_ceo
  AND g.grade_code != 'DEFAULT'
  AND e.emp_id % 10 = 8
  AND (g.grade_order - @g_min_order) >= 2                   -- 최소 1회 진급 발생 보장
  AND step.n <= (g.grade_order - @g_min_order - 1)          -- 한 단계 덜
  AND DATE_ADD(e.emp_hire_date, INTERVAL 4 * step.n YEAR) <= CURRENT_DATE;

-- ── 1-D. 케이스 B (emp_id % 10 = 9) — 3년 주기 빠른 진급 (자연 정체) ──
--   3년 주기로 빠르게 진급했지만, 입사가 옛날일수록 마지막 진급 시점이 자연히 과거
--   분석 도구가 "마지막 진급 후 4년+ 경과 + 등급 A+" 조건으로 케이스 B 추출
INSERT INTO hr_order
  (company_id, emp_id, create_by, effective_date, order_type, is_notified, notified_at,
   status, form_values, form_snapshot, form_version, created_at)
SELECT
  @cid,
  e.emp_id,
  @e_ceo,
  DATE_ADD(e.emp_hire_date, INTERVAL 3 * step.n YEAR),
  'PROMOTION',
  TRUE,
  DATE_ADD(DATE_ADD(e.emp_hire_date, INTERVAL 3 * step.n YEAR), INTERVAL -7 DAY),
  'APPLIED',
  JSON_OBJECT(
    'orderTitle',  CONCAT(YEAR(DATE_ADD(e.emp_hire_date, INTERVAL 3 * step.n YEAR)), '년 정기 인사발령'),
    'orderReason', CONCAT('근속 ', 3 * step.n, '년차 우수자 조기 진급')
  ),
  '[]',
  UNIX_TIMESTAMP() * 1000,
  NOW(6)
FROM employee e
JOIN grade g ON g.grade_id = e.grade_id
CROSS JOIN (SELECT 1 AS n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5) step
WHERE e.company_id = @cid
  AND e.emp_status = 'ACTIVE'
  AND e.title_id != @t_head
  AND e.emp_id != @e_ceo
  AND g.grade_code != 'DEFAULT'
  AND e.emp_id % 10 = 9
  AND step.n <= (g.grade_order - @g_min_order)
  AND DATE_ADD(e.emp_hire_date, INTERVAL 3 * step.n YEAR) <= CURRENT_DATE;

-- =====================================================================
-- STEP 2. TRANSFER (전보) — emp_id % 10 IN (1, 4) 1회/사원
-- ---------------------------------------------------------------------
-- 시나리오: 입사 후 3년차에 다른 부서에서 현재 부서로 전보
-- before_dept ↔ current_dept 매핑은 STEP 5의 hr_order_detail에서 결정
-- =====================================================================
INSERT INTO hr_order
  (company_id, emp_id, create_by, effective_date, order_type, is_notified, notified_at,
   status, form_values, form_snapshot, form_version, created_at)
SELECT
  @cid,
  e.emp_id,
  @e_ceo,
  DATE_ADD(e.emp_hire_date, INTERVAL 3 YEAR),
  'TRANSFER',
  TRUE,
  DATE_ADD(DATE_ADD(e.emp_hire_date, INTERVAL 3 YEAR), INTERVAL -7 DAY),
  'APPLIED',
  JSON_OBJECT(
    'orderTitle',  CONCAT(YEAR(DATE_ADD(e.emp_hire_date, INTERVAL 3 YEAR)), '년 정기 인사이동'),
    'orderReason', '부서간 인력 재배치'
  ),
  '[]',
  UNIX_TIMESTAMP() * 1000,
  NOW(6)
FROM employee e
JOIN department d_curr ON d_curr.dept_id = e.dept_id
WHERE e.company_id = @cid
  AND e.emp_status = 'ACTIVE'
  AND e.title_id != @t_head
  AND e.emp_id != @e_ceo
  AND e.emp_id % 10 IN (1, 4)
  AND d_curr.dept_code IN ('HR', 'FIN', 'DEV', 'INF', 'SALES', 'MKT')   -- EXEC 제외
  AND DATE_ADD(e.emp_hire_date, INTERVAL 3 YEAR) <= CURRENT_DATE;

-- =====================================================================
-- STEP 3. TITLE_CHANGE (보직변경) — 현재 T-LEAD 사원 1회/사원
-- ---------------------------------------------------------------------
-- 시나리오: 입사 후 5년차에 T-MEMBER → T-LEAD 보직변경
-- =====================================================================
INSERT INTO hr_order
  (company_id, emp_id, create_by, effective_date, order_type, is_notified, notified_at,
   status, form_values, form_snapshot, form_version, created_at)
SELECT
  @cid,
  e.emp_id,
  @e_ceo,
  DATE_ADD(e.emp_hire_date, INTERVAL 5 YEAR),
  'TITLE_CHANGE',
  TRUE,
  DATE_ADD(DATE_ADD(e.emp_hire_date, INTERVAL 5 YEAR), INTERVAL -7 DAY),
  'APPLIED',
  JSON_OBJECT(
    'orderTitle',  CONCAT(YEAR(DATE_ADD(e.emp_hire_date, INTERVAL 5 YEAR)), '년 보직변경 발령'),
    'orderReason', '팀장 임명'
  ),
  '[]',
  UNIX_TIMESTAMP() * 1000,
  NOW(6)
FROM employee e
WHERE e.company_id = @cid
  AND e.emp_status = 'ACTIVE'
  AND e.title_id = @t_lead                  -- 현재 T-LEAD 인 사원만
  AND e.emp_id != @e_ceo
  AND DATE_ADD(e.emp_hire_date, INTERVAL 5 YEAR) <= CURRENT_DATE;

-- =====================================================================
-- STEP 4. PROMOTION 상세 (hr_order_detail / GRADE) — ROW_NUMBER 매칭
-- ---------------------------------------------------------------------
-- ROW_NUMBER로 사원별 진급 순번 매기고, 순번 → before/after grade_order 매핑
--   step_n=1 → before=g_min, after=g_min+1
--   step_n=2 → before=g_min+1, after=g_min+2
-- WHERE order_type = 'PROMOTION' 으로 TRANSFER/TITLE_CHANGE 와 분리
-- =====================================================================
INSERT INTO hr_order_detail (order_id, target_type, before_id, after_id)
SELECT
  o.order_id,
  'GRADE',
  g_from.grade_id,
  g_to.grade_id
FROM (
  SELECT
    order_id,
    emp_id,
    ROW_NUMBER() OVER (PARTITION BY emp_id ORDER BY effective_date, order_id) AS step_n
  FROM hr_order
  WHERE company_id = @cid AND order_type = 'PROMOTION'
) o
JOIN grade g_from ON g_from.company_id = @cid
                 AND g_from.grade_code != 'DEFAULT'
                 AND g_from.grade_order = (@g_min_order + o.step_n - 1)
JOIN grade g_to ON g_to.company_id = @cid
                AND g_to.grade_code != 'DEFAULT'
                AND g_to.grade_order = (@g_min_order + o.step_n);

-- =====================================================================
-- STEP 5. TRANSFER 상세 (hr_order_detail / DEPARTMENT)
-- ---------------------------------------------------------------------
-- 이전 부서 ↔ 현재 부서 매핑(고정):
--   HR    → DEV
--   FIN   → SALES
--   DEV   → INF
--   INF   → DEV
--   SALES → MKT
--   MKT   → SALES
-- after_id = 현재 employee.dept_id (마지막 발령의 after = 현재 부서, 일관성)
-- =====================================================================
INSERT INTO hr_order_detail (order_id, target_type, before_id, after_id)
SELECT
  o.order_id,
  'DEPARTMENT',
  d_prev.dept_id,
  e.dept_id
FROM hr_order o
JOIN employee e ON e.emp_id = o.emp_id
JOIN department d_curr ON d_curr.dept_id = e.dept_id
JOIN department d_prev ON d_prev.company_id = @cid
                       AND d_prev.dept_code = CASE d_curr.dept_code
                          WHEN 'HR'    THEN 'DEV'
                          WHEN 'FIN'   THEN 'SALES'
                          WHEN 'DEV'   THEN 'INF'
                          WHEN 'INF'   THEN 'DEV'
                          WHEN 'SALES' THEN 'MKT'
                          WHEN 'MKT'   THEN 'SALES'
                       END
WHERE o.company_id = @cid AND o.order_type = 'TRANSFER';

-- =====================================================================
-- STEP 6. TITLE_CHANGE 상세 (hr_order_detail / TITLE)
-- ---------------------------------------------------------------------
-- T-MEMBER → T-LEAD (대상자가 T-LEAD 인 사원이므로 after = T-LEAD 고정)
-- =====================================================================
INSERT INTO hr_order_detail (order_id, target_type, before_id, after_id)
SELECT
  o.order_id,
  'TITLE',
  @t_mem,
  @t_lead
FROM hr_order o
WHERE o.company_id = @cid AND o.order_type = 'TITLE_CHANGE';

-- =====================================================================
-- STEP 7. RESIGN (퇴사) — resign 테이블 시드
-- ---------------------------------------------------------------------
-- 백엔드 history() 는 resign.retire_status='RESIGNED' 인 건만 발령 이력에 합성
-- (ACTIVE/CONFIRMED 는 신청/대기 단계라 이력 미포함)
--
-- 02 시드 기준 RESIGNED 사원: emp035 (2024-12-31), emp076 (2025-03-15)
-- registered_date = 퇴직예정일 - 30일 (한 달 전 신청 가정)
-- processed_at    = 퇴직일 18:00 (퇴근시간 처리 가정)
-- =====================================================================
INSERT INTO resign (
  emp_id, grade_id, title_id, dept_id,
  processed_at, doc_id,
  retire_status, is_deleted,
  resign_date, registered_date
)
SELECT
  e.emp_id, e.grade_id, e.title_id, e.dept_id,
  CONCAT(e.emp_resign, ' 18:00:00'),
  NULL,
  'RESIGNED', FALSE,
  e.emp_resign,
  DATE_SUB(e.emp_resign, INTERVAL 30 DAY)
FROM employee e
WHERE e.company_id = @cid
  AND e.emp_status = 'RESIGNED'
  AND e.emp_resign IS NOT NULL;

-- =====================================================================
-- 검증 쿼리
-- =====================================================================
SELECT '=== 인사발령 합산 (발령유형별) ===' AS section;
SELECT order_type, COUNT(*) AS cnt
FROM hr_order
WHERE company_id = @cid
GROUP BY order_type
ORDER BY order_type;

SELECT '=== 인사발령 상세 (target_type별) ===' AS section;
SELECT d.target_type, COUNT(*) AS cnt
FROM hr_order_detail d
JOIN hr_order o ON o.order_id = d.order_id
WHERE o.company_id = @cid
GROUP BY d.target_type
ORDER BY d.target_type;

SELECT '=== resign (퇴사) 시드 ===' AS section;
SELECT r.resign_id, e.emp_num, e.emp_name, r.retire_status, r.resign_date, r.registered_date, r.processed_at
FROM resign r
JOIN employee e ON e.emp_id = r.emp_id
WHERE e.company_id = @cid
ORDER BY r.resign_date;

SELECT '=== PROMOTION 패턴별 분포 (emp_id % 10) ===' AS section;
SELECT
  CASE
    WHEN e.emp_id % 10 <= 5      THEN '통상 (4년 주기)'
    WHEN e.emp_id % 10 IN (6,7)  THEN '빠름 (3년 주기)'
    WHEN e.emp_id % 10 = 8       THEN '케이스 A 후보 (정체)'
    WHEN e.emp_id % 10 = 9       THEN '케이스 B 후보 (우수자 정체)'
  END AS pattern,
  COUNT(DISTINCT e.emp_id) AS emp_cnt,
  COUNT(o.order_id)        AS order_cnt
FROM employee e
LEFT JOIN hr_order o ON o.emp_id = e.emp_id AND o.company_id = @cid AND o.order_type = 'PROMOTION'
WHERE e.company_id = @cid
  AND e.emp_status = 'ACTIVE'
  AND e.title_id != @t_head
  AND e.emp_id != @e_ceo
GROUP BY pattern
ORDER BY pattern;

SELECT '=== TRANSFER 시드 샘플 (상위 10건) ===' AS section;
SELECT
  e.emp_num, e.emp_name,
  d_prev.dept_name AS '이전 부서',
  d_curr.dept_name AS '현재 부서',
  o.effective_date AS '전보일'
FROM hr_order o
JOIN employee e ON e.emp_id = o.emp_id
JOIN hr_order_detail dt ON dt.order_id = o.order_id
JOIN department d_prev ON d_prev.dept_id = dt.before_id
JOIN department d_curr ON d_curr.dept_id = dt.after_id
WHERE o.company_id = @cid AND o.order_type = 'TRANSFER'
ORDER BY o.effective_date
LIMIT 10;

SELECT '=== TITLE_CHANGE 시드 (전체) ===' AS section;
SELECT
  e.emp_num, e.emp_name,
  t_prev.title_name AS '이전 직책',
  t_curr.title_name AS '현재 직책',
  o.effective_date AS '보직변경일'
FROM hr_order o
JOIN employee e ON e.emp_id = o.emp_id
JOIN hr_order_detail dt ON dt.order_id = o.order_id
JOIN title t_prev ON t_prev.title_id = dt.before_id
JOIN title t_curr ON t_curr.title_id = dt.after_id
WHERE o.company_id = @cid AND o.order_type = 'TITLE_CHANGE'
ORDER BY o.effective_date;

SELECT '=== 케이스 A 후보 (진급 횟수 < grade_distance, 마지막 진급 6년+ 전) ===' AS section;
SELECT
  e.emp_num, e.emp_name,
  g.grade_name AS '현 직급',
  (g.grade_order - @g_min_order) AS '필요 진급 횟수',
  (SELECT COUNT(*) FROM hr_order WHERE emp_id = e.emp_id AND company_id = @cid AND order_type = 'PROMOTION') AS '실제 진급 횟수',
  (SELECT MAX(effective_date) FROM hr_order WHERE emp_id = e.emp_id AND company_id = @cid AND order_type = 'PROMOTION') AS '마지막 진급일'
FROM employee e
JOIN grade g ON g.grade_id = e.grade_id
WHERE e.company_id = @cid
  AND e.emp_status = 'ACTIVE'
  AND e.title_id != @t_head
  AND e.emp_id % 10 = 8
  AND (g.grade_order - @g_min_order) >= 2
ORDER BY e.emp_num;

SELECT '=== 케이스 B 후보 (3년 주기 빠른 진급, 마지막 진급 4년+ 경과) ===' AS section;
SELECT
  e.emp_num, e.emp_name,
  g.grade_name AS '현 직급',
  last_promo.last_date AS '마지막 진급일',
  TIMESTAMPDIFF(YEAR, last_promo.last_date, CURRENT_DATE) AS '경과 년수'
FROM employee e
JOIN grade g ON g.grade_id = e.grade_id
JOIN (
  SELECT emp_id, MAX(effective_date) AS last_date
  FROM hr_order WHERE company_id = @cid AND order_type = 'PROMOTION'
  GROUP BY emp_id
) last_promo ON last_promo.emp_id = e.emp_id
WHERE e.company_id = @cid
  AND e.emp_status = 'ACTIVE'
  AND e.title_id != @t_head
  AND e.emp_id % 10 = 9
  AND TIMESTAMPDIFF(YEAR, last_promo.last_date, CURRENT_DATE) >= 4
ORDER BY e.emp_num;

SELECT '=== 사원별 발령 이력 건수 분포 (PROMOTION+TRANSFER+TITLE_CHANGE) ===' AS section;
SELECT
  order_cnt AS '발령 이력 건수',
  COUNT(*)  AS '사원 수'
FROM (
  SELECT
    e.emp_id,
    (SELECT COUNT(*) FROM hr_order WHERE emp_id = e.emp_id AND company_id = @cid) AS order_cnt
  FROM employee e
  WHERE e.company_id = @cid
    AND e.emp_status = 'ACTIVE'
    AND e.title_id != @t_head
    AND e.emp_id != @e_ceo
) sub
GROUP BY order_cnt
ORDER BY order_cnt;

COMMIT;
