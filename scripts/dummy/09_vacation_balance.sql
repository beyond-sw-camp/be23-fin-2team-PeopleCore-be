use peoplecore;

-- =====================================================================
-- HR Service 더미 휴가 잔여 데이터 (VacationBalance)
-- ---------------------------------------------------------------------
-- 선행 조건:
--   1) 회사 'peoplecore' + 01_hr_master_data.sql + 02_hr_employees.sql 실행 완료
--   2) 회사 생성 시 자동 시드된 vacation_type 2건 (MONTHLY/ANNUAL) 존재
--
-- 정책: HIRE 기준 (입사일), 연차 11구간 기본 규칙, 월차 캡 11일
-- 기준일: CURDATE() (오늘)
-- 대상: emp_status IN ('ACTIVE','ON_LEAVE') 사원 (RESIGNED 제외)
--
-- 산출:
--   (A) ANNUAL  : yos>=1 → 최신 입사기념일 기준 1행
--   (B) MONTHLY : yos>=1 → 입사 1년차 월차 이력 (total=11, expired=11)
--   (C) MONTHLY : yos<1  → 만근 도달분만 calendar-year별로 그룹핑
-- =====================================================================

SET @company_name := 'peoplecore';
SET @cid := (SELECT company_id FROM company WHERE company_name = @company_name);

SET @vt_monthly := (SELECT type_id FROM vacation_type WHERE company_id=@cid AND type_code='MONTHLY');
SET @vt_annual  := (SELECT type_id FROM vacation_type WHERE company_id=@cid AND type_code='ANNUAL');

SELECT
  IFNULL(BIN_TO_UUID(@cid), CONCAT('❌ 회사 없음: ', @company_name)) AS company,
  @vt_monthly AS monthly_type_id,
  @vt_annual  AS annual_type_id;


-- =====================================================================
-- (A) ANNUAL — 근속 1년 이상 사원의 현재 연차 잔여
-- ---------------------------------------------------------------------
-- granted_at = hire_date + yos년 (= 최신 입사기념일)
-- expires_at = granted_at + 1년 - 1일
-- balance_year = YEAR(granted_at)
-- total_days = 근속연수 매칭 규칙 (15~25)
-- used/pending/expired = 0 (사용 이력 없는 깨끗한 상태)
-- =====================================================================
INSERT INTO vacation_balance (
  company_id, type_id, emp_id, balance_year,
  total_days, used_days, pending_days, expired_days,
  granted_at, expires_at, version, created_at, updated_at
)
SELECT
  e.company_id,
  @vt_annual,
  e.emp_id,
  YEAR(DATE_ADD(e.emp_hire_date,
                INTERVAL TIMESTAMPDIFF(YEAR, e.emp_hire_date, CURDATE()) YEAR)) AS balance_year,
  CASE
    WHEN TIMESTAMPDIFF(YEAR, e.emp_hire_date, CURDATE()) BETWEEN 1  AND 2  THEN 15
    WHEN TIMESTAMPDIFF(YEAR, e.emp_hire_date, CURDATE()) BETWEEN 3  AND 4  THEN 16
    WHEN TIMESTAMPDIFF(YEAR, e.emp_hire_date, CURDATE()) BETWEEN 5  AND 6  THEN 17
    WHEN TIMESTAMPDIFF(YEAR, e.emp_hire_date, CURDATE()) BETWEEN 7  AND 8  THEN 18
    WHEN TIMESTAMPDIFF(YEAR, e.emp_hire_date, CURDATE()) BETWEEN 9  AND 10 THEN 19
    WHEN TIMESTAMPDIFF(YEAR, e.emp_hire_date, CURDATE()) BETWEEN 11 AND 12 THEN 20
    WHEN TIMESTAMPDIFF(YEAR, e.emp_hire_date, CURDATE()) BETWEEN 13 AND 14 THEN 21
    WHEN TIMESTAMPDIFF(YEAR, e.emp_hire_date, CURDATE()) BETWEEN 15 AND 16 THEN 22
    WHEN TIMESTAMPDIFF(YEAR, e.emp_hire_date, CURDATE()) BETWEEN 17 AND 18 THEN 23
    WHEN TIMESTAMPDIFF(YEAR, e.emp_hire_date, CURDATE()) BETWEEN 19 AND 20 THEN 24
    ELSE 25
  END AS total_days,
  0, 0, 0,
  DATE_ADD(e.emp_hire_date,
           INTERVAL TIMESTAMPDIFF(YEAR, e.emp_hire_date, CURDATE()) YEAR) AS granted_at,
  DATE_SUB(
    DATE_ADD(e.emp_hire_date,
             INTERVAL TIMESTAMPDIFF(YEAR, e.emp_hire_date, CURDATE()) + 1 YEAR),
    INTERVAL 1 DAY) AS expires_at,
  0, NOW(), NOW()
FROM employee e
WHERE e.company_id = @cid
  AND e.emp_status IN ('ACTIVE','ON_LEAVE')
  AND TIMESTAMPDIFF(YEAR, e.emp_hire_date, CURDATE()) >= 1;


-- =====================================================================
-- (B) MONTHLY 이력 — 근속 1년 이상 사원의 입사 1년차 월차 (만료 처리됨)
-- ---------------------------------------------------------------------
-- balance_year = YEAR(emp_hire_date)  (첫 적립이 일어난 해)
-- granted_at   = hire_date + 1개월     (첫 적립일)
-- expires_at   = hire_date + 1년 - 1일 (1주년 전날 만료)
-- total=11, expired=11 → 잔여 0, 수당 계산에서는 expired 제외
-- =====================================================================
INSERT INTO vacation_balance (
  company_id, type_id, emp_id, balance_year,
  total_days, used_days, pending_days, expired_days,
  granted_at, expires_at, version, created_at, updated_at
)
SELECT
  e.company_id,
  @vt_monthly,
  e.emp_id,
  YEAR(e.emp_hire_date) AS balance_year,
  11.00, 0, 0, 11.00,
  DATE_ADD(e.emp_hire_date, INTERVAL 1 MONTH) AS granted_at,
  DATE_SUB(DATE_ADD(e.emp_hire_date, INTERVAL 1 YEAR), INTERVAL 1 DAY) AS expires_at,
  0, NOW(), NOW()
FROM employee e
WHERE e.company_id = @cid
  AND e.emp_status IN ('ACTIVE','ON_LEAVE')
  AND TIMESTAMPDIFF(YEAR, e.emp_hire_date, CURDATE()) >= 1;


-- =====================================================================
-- (C) MONTHLY 적립중 — 근속 1년 미만 사원의 진행형 월차
-- ---------------------------------------------------------------------
-- 1~11 시퀀스 × 사원 → hire_date + n월 이 today 이전이고 1년차 이내인 n 만 필터
-- balance_year = YEAR(hire_date + n월)   (적립 시점 달력연도)
-- granted_at   = 해당 연도 첫 적립일      (MIN)
-- expires_at   = hire_date + 1년 - 1일   (사원 공통)
-- total_days   = 해당 연도 만근 월수      (COUNT)
-- =====================================================================
INSERT INTO vacation_balance (
  company_id, type_id, emp_id, balance_year,
  total_days, used_days, pending_days, expired_days,
  granted_at, expires_at, version, created_at, updated_at
)
SELECT
  e.company_id,
  @vt_monthly,
  e.emp_id,
  YEAR(DATE_ADD(e.emp_hire_date, INTERVAL n.n MONTH)) AS balance_year,
  COUNT(*) AS total_days,
  0, 0, 0,
  MIN(DATE_ADD(e.emp_hire_date, INTERVAL n.n MONTH)) AS granted_at,
  DATE_SUB(DATE_ADD(e.emp_hire_date, INTERVAL 1 YEAR), INTERVAL 1 DAY) AS expires_at,
  0, NOW(), NOW()
FROM employee e
CROSS JOIN (
  SELECT 1 AS n UNION ALL SELECT 2  UNION ALL SELECT 3  UNION ALL SELECT 4
  UNION ALL SELECT 5 UNION ALL SELECT 6  UNION ALL SELECT 7  UNION ALL SELECT 8
  UNION ALL SELECT 9 UNION ALL SELECT 10 UNION ALL SELECT 11
) n
WHERE e.company_id = @cid
  AND e.emp_status IN ('ACTIVE','ON_LEAVE')
  AND TIMESTAMPDIFF(YEAR, e.emp_hire_date, CURDATE()) < 1
  AND DATE_ADD(e.emp_hire_date, INTERVAL n.n MONTH) <= CURDATE()
GROUP BY
  e.company_id, e.emp_id, e.emp_hire_date,
  YEAR(DATE_ADD(e.emp_hire_date, INTERVAL n.n MONTH));


-- =====================================================================
-- [검증 쿼리]
-- =====================================================================
-- SELECT vt.type_code, COUNT(*) AS cnt
--   FROM vacation_balance vb JOIN vacation_type vt ON vb.type_id = vt.type_id
--  WHERE vb.company_id = @cid
--  GROUP BY vt.type_code;
--
-- SELECT e.emp_num, e.emp_name, e.emp_hire_date,
--        TIMESTAMPDIFF(YEAR, e.emp_hire_date, CURDATE()) AS yos,
--        vt.type_code, vb.balance_year, vb.total_days, vb.expired_days,
--        vb.granted_at, vb.expires_at
--   FROM vacation_balance vb
--   JOIN employee e      ON vb.emp_id  = e.emp_id
--   JOIN vacation_type vt ON vb.type_id = vt.type_id
--  WHERE vb.company_id = @cid
--  ORDER BY e.emp_num, vt.type_code, vb.balance_year;
