use peoplecore;
-- =====================================================================
-- HR Service 더미 퇴직금/연차수당 (Vacation Balance + Severance + Leave Allowance)
-- ---------------------------------------------------------------------
-- 선행 조건:
--   1) 02_hr_employees.sql      (사원 + resign)
--   2) 03_hr_salary_contracts.sql (연봉계약)
--   3) 06_payroll_history.sql   (payroll_runs 가 있어야 leave_allowance.applied_payroll_run_id 매핑)
--
-- [구성]
--   1) vacation_balance       : 전 사원 × {2024, 2025, 2026} × type_code='ANNUAL'
--                                회사 정책(HIRE)에 맞춰 입사기념일 기준 유효기간
--   2) leave_allowance        : 전 사원 2025년 입사기념일분 + 퇴직자 7명 (RESIGNED)
--   3) severance_pays         : 퇴직자 7명 (기존 035, 076 + 신규 116~120)
--
-- [근속연수별 연차 부여 (근로기준법)]
--   < 1년     : 0일 (월차로 별도 적립)
--   1~2년차   : 15일
--   3~4년차   : 16일
--   5~6년차   : 17일
--   ...       : 2년당 +1일
--   21년차+   : 25일 (상한)
--
-- [퇴직금 계산식 (SeveranceService.java)]
--   service_days     = DATEDIFF(resign_date, hire_date)
--   service_years    = ROUND(service_days / 365.0, 2)
--   last3month_pay = (기본급+직책수당+식대+교통비) × 3 = total_amount / 4  (단순화)
--   last3month_days= DATEDIFF(resign_date, resign_date - 3 MONTH)
--   avg_daily_wage   = last3month_pay / last3month_days
--   severance_amount = ROUND(avg_daily_wage × 30 × service_days / 365)
--   tax_amount       = DB/DC(IRP 이전) 0, 직접지급은 ROUND(severance_amount × 5%) (퇴직소득세 단순화)
--   local_income_tax = DB/DC(IRP 이전) 0, 직접지급은 ROUND(tax_amount × 10%)      (지방소득세)
--   net_amount       = severance_amount - tax_amount - local_income_tax
--   dc_deposited     = (DC형이면 severance × 70%, else 0)  (적립 시뮬)
--   dc_diff_amount   = (DC형이면 severance - dc_deposited, else 0)
--   irp_transfer     = retirement_type IN (DC, DB)
--
-- [실행 방법]
--   $ mysql -u <user> -p peoplecore < 07_severance_leave_allowance.sql
-- =====================================================================

SET @company_name := 'peoplecore';
SET @cid := (SELECT company_id FROM company WHERE company_name = @company_name);

-- 처리자 (인사팀장)
SET @actor_emp_id := (SELECT emp_id FROM employee WHERE company_id=@cid AND emp_num='EMP-2025-005');

-- 연차 type_id (typeCode='ANNUAL', 회사 생성시 자동 시드)
SET @annual_type_id := (SELECT type_id FROM vacation_type
                         WHERE company_id=@cid AND type_code='ANNUAL');

SELECT
  IFNULL(BIN_TO_UUID(@cid), '❌ 회사 없음') AS company,
  @actor_emp_id AS actor_emp_id,
  @annual_type_id AS annual_type_id;


-- =====================================================================
-- ▼ 기존 시드 정리 (회사 한정) ▼
-- =====================================================================
DELETE FROM leave_allowance WHERE company_id = @cid AND year IN (2024, 2025, 2026);
DELETE FROM severance_pays  WHERE company_id = @cid;
DELETE FROM vacation_balance
 WHERE company_id = @cid AND type_id = @annual_type_id
   AND balance_year IN (2024, 2025, 2026);


-- =====================================================================
-- 1) vacation_balance — 전 사원 × {2024, 2025, 2026} × ANNUAL
-- ---------------------------------------------------------------------
--   total_days : 근속연수 기준 (근기법 15~25일)
--                입사연도분은 첫 1년 월차 소멸/수당 데모용으로 11일
--   used_days  : CRC32 결정론적 0 ~ total 까지 분포
--   pending/expired : 0
--   granted_at = balance_year의 입사기념일
--   expires_at = 다음 입사기념일 전날
--   version = 0
-- =====================================================================

INSERT INTO vacation_balance (
  company_id, type_id, emp_id, balance_year,
  total_days, used_days, pending_days, expired_days,
  granted_at, expires_at, version,
  created_at, updated_at
)
SELECT
  @cid, @annual_type_id, e.emp_id, y.yr,
  -- total_days (근로기준법 누진)
  CASE
    WHEN y.yr = YEAR(e.emp_hire_date) THEN 11.00
    WHEN (y.yr - YEAR(e.emp_hire_date)) < 1 THEN 0.00
    ELSE LEAST(25.00, 15 + GREATEST(0, FLOOR((y.yr - YEAR(e.emp_hire_date) - 1) / 2)))
  END AS total_days,
  -- used_days: total 의 30~80% 결정론적
  CASE
    WHEN y.yr = YEAR(e.emp_hire_date) THEN
      ROUND(11 * ((CRC32(CONCAT(e.emp_id, y.yr)) % 50 + 30) / 100.0), 1)
    WHEN (y.yr - YEAR(e.emp_hire_date)) < 1 THEN 0.00
    ELSE ROUND(
           LEAST(25, 15 + GREATEST(0, FLOOR((y.yr - YEAR(e.emp_hire_date) - 1) / 2)))
           * ((CRC32(CONCAT(e.emp_id, y.yr)) % 50 + 30) / 100.0),
           1)
  END AS used_days,
  0.00, 0.00,
  DATE(CONCAT(y.yr, '-', DATE_FORMAT(e.emp_hire_date, '%m-%d'))),
  DATE_SUB(DATE_ADD(DATE(CONCAT(y.yr, '-', DATE_FORMAT(e.emp_hire_date, '%m-%d'))), INTERVAL 1 YEAR), INTERVAL 1 DAY),
  0,
  NOW(), NOW()
FROM employee e
CROSS JOIN (
  SELECT 2024 AS yr UNION ALL SELECT 2025 UNION ALL SELECT 2026
) y
WHERE e.company_id = @cid
  AND e.emp_hire_date <= DATE(CONCAT(y.yr, '-12-31'))
  -- 퇴직자: 퇴직 연도까지만 잔여 시드 (그 다음 해는 잔여 의미 없음)
  AND (e.emp_resign IS NULL OR YEAR(e.emp_resign) >= y.yr);


-- =====================================================================
-- 2) leave_allowance — 입사기념일 기준 미사용 연차수당 (재직자)
-- ---------------------------------------------------------------------
--   ANNIVERSARY 타입.
--   2024 balance 만료분: allowance.year=2025, status=APPLIED
--   2026년분은 seed 하지 않고 화면 조회 시 LeaveAllowanceService 가
--   입사월별 PENDING 을 생성한다. 예: 2026-04 조회 → 갈태양/강민서 모두 대상
--
--   amount = unused × daily_wage = (total - used) × ROUND(monthly_salary / 209 × 8)
-- =====================================================================

-- 2024 balance 만료분 (APPLIED) — 재직자만
INSERT INTO leave_allowance (
  company_id, emp_id, year, allowance_type,
  normal_monthly_salary, daily_wage,
  total_leave_days, used_leave_days, unused_leave_days, allowance_amount,
  status, applied_payroll_run_id, applied_month, resign_date,
  created_at, updated_at
)
SELECT
  @cid, e.emp_id, 2025, 'ANNIVERSARY',
  FLOOR(sc.total_amount / 12) AS normal_monthly_salary,
  ROUND(FLOOR(sc.total_amount / 12) / 209 * 8) AS daily_wage,
  vb.total_days,
  vb.used_days,
  (vb.total_days - vb.used_days) AS unused_leave_days,
  ROUND((vb.total_days - vb.used_days) * ROUND(FLOOR(sc.total_amount / 12) / 209 * 8)) AS allowance_amount,
  'APPLIED',
  (SELECT payroll_run_id FROM payroll_runs
    WHERE company_id=@cid AND pay_year_month=DATE_FORMAT(DATE_ADD(vb.expires_at, INTERVAL 1 DAY), '%Y-%m') LIMIT 1),
  DATE_FORMAT(DATE_ADD(vb.expires_at, INTERVAL 1 DAY), '%Y-%m'),
  NULL,
  NOW(), NOW()
FROM employee e
JOIN vacation_balance vb
  ON vb.emp_id = e.emp_id AND vb.balance_year = 2024 AND vb.type_id = @annual_type_id
JOIN salary_contract sc ON sc.emp_id = e.emp_id AND sc.company_id = @cid
WHERE e.company_id = @cid
  AND e.emp_status <> 'RESIGNED'                  -- 퇴직자는 RESIGNED 타입으로 별도
  AND vb.total_days > vb.used_days;               -- 미사용 있을 때만

-- 퇴직자분 (RESIGNED 타입) — 퇴직년도 기준 미사용 연차 정산
INSERT INTO leave_allowance (
  company_id, emp_id, year, allowance_type,
  normal_monthly_salary, daily_wage,
  total_leave_days, used_leave_days, unused_leave_days, allowance_amount,
  status, applied_payroll_run_id, applied_month, resign_date,
  created_at, updated_at
)
SELECT
  @cid, e.emp_id, YEAR(e.emp_resign), 'RESIGNED',
  FLOOR(sc.total_amount / 12),
  ROUND(FLOOR(sc.total_amount / 12) / 209 * 8),
  vb.total_days, vb.used_days,
  (vb.total_days - vb.used_days),
  ROUND((vb.total_days - vb.used_days) * ROUND(FLOOR(sc.total_amount / 12) / 209 * 8)),
  'APPLIED',
  NULL, NULL,
  e.emp_resign,
  NOW(), NOW()
FROM employee e
JOIN vacation_balance vb
  ON vb.emp_id = e.emp_id
 AND vb.balance_year = YEAR(e.emp_resign)
 AND vb.type_id = @annual_type_id
JOIN salary_contract sc ON sc.emp_id = e.emp_id AND sc.company_id = @cid
WHERE e.company_id = @cid
  AND e.emp_status = 'RESIGNED'
  AND vb.total_days >= vb.used_days;


-- =====================================================================
-- 3) severance_pays — 퇴직자 7명 (기존 035, 076 + 신규 116~120)
-- ---------------------------------------------------------------------
--   sev_status = PAID (전부 지급완료)
--   transfer_date = 퇴직일 + 14일 (근로기준법 14일 이내 지급 의무)
--   confirmed/paid: 처리자 = 인사팀장 (@actor_emp_id)
--   approval_doc_id = NULL (외부 결재시스템 ID 시드 안 함)
--   스냅샷 컬럼 (emp_name/dept_name/grade_name/work_group_name) 박음
-- =====================================================================

INSERT INTO severance_pays (
  emp_id, company_id, hire_date, resign_date, retirement_type,
  service_years, service_days,
  last3month_pay, last_year_bonus,
  annual_leave_for_avg_wage, annual_leave_on_retirement,
  last3month_days, avg_daily_wage,
  severance_amount, tax_amount, local_income_tax,
  tax_year, irp_transfer, net_amount,
  dc_deposited_total, dc_diff_amount,
  sev_status, approval_doc_id, transfer_date,
  confirmed_by, confirmed_at, paid_by, paid_at,
  emp_name, dept_name, grade_name, work_group_name,
  created_at, updated_at
)
SELECT
  e.emp_id, @cid,
  e.emp_hire_date, e.emp_resign, e.retirement_type,
  ROUND(DATEDIFF(e.emp_resign, e.emp_hire_date) / 365.0, 2) AS service_years,
  DATEDIFF(e.emp_resign, e.emp_hire_date) AS service_days,
  -- last3month_pay = (월급여 = 연봉/12) × 3
  FLOOR(sc.total_amount / 4) AS last3month_pay,
  0,        -- last_year_bonus
  0,        -- annual_leave_for_avg_wage
  0,        -- annual_leave_on_retirement
  -- last3month_days = 직전 3개월 일수
  DATEDIFF(e.emp_resign, DATE_SUB(e.emp_resign, INTERVAL 3 MONTH)) AS last3month_days,
  -- avg_daily_wage = last3month_pay / last3month_days  (DECIMAL(15,2))
  ROUND(FLOOR(sc.total_amount / 4)
        / DATEDIFF(e.emp_resign, DATE_SUB(e.emp_resign, INTERVAL 3 MONTH)), 2) AS avg_daily_wage,
  -- severance_amount = avg_daily_wage × 30 × service_days / 365
  ROUND(
    (FLOOR(sc.total_amount / 4) / DATEDIFF(e.emp_resign, DATE_SUB(e.emp_resign, INTERVAL 3 MONTH)))
    * 30 * DATEDIFF(e.emp_resign, e.emp_hire_date) / 365
  ) AS severance_amount,
  -- tax_amount = DB/DC(IRP 이전) 0, 직접지급은 severance × 5%
  CASE WHEN e.retirement_type IN ('DC', 'DB') THEN 0 ELSE
    ROUND(
      (FLOOR(sc.total_amount / 4) / DATEDIFF(e.emp_resign, DATE_SUB(e.emp_resign, INTERVAL 3 MONTH)))
      * 30 * DATEDIFF(e.emp_resign, e.emp_hire_date) / 365 * 0.05
    ) END AS tax_amount,
  -- local_income_tax = DB/DC(IRP 이전) 0, 직접지급은 tax × 10%
  CASE WHEN e.retirement_type IN ('DC', 'DB') THEN 0 ELSE
    ROUND(
      (FLOOR(sc.total_amount / 4) / DATEDIFF(e.emp_resign, DATE_SUB(e.emp_resign, INTERVAL 3 MONTH)))
      * 30 * DATEDIFF(e.emp_resign, e.emp_hire_date) / 365 * 0.05 * 0.10
    ) END AS local_income_tax,
  YEAR(e.emp_resign) AS tax_year,
  -- irp_transfer = DC 또는 DB
  CASE WHEN e.retirement_type IN ('DC', 'DB') THEN TRUE ELSE FALSE END AS irp_transfer,
  -- net_amount = DB/DC(IRP 이전)은 세액 차감 없음, 직접지급은 tax/local_tax 차감
  CASE WHEN e.retirement_type IN ('DC', 'DB') THEN
    ROUND(
      (FLOOR(sc.total_amount / 4) / DATEDIFF(e.emp_resign, DATE_SUB(e.emp_resign, INTERVAL 3 MONTH)))
      * 30 * DATEDIFF(e.emp_resign, e.emp_hire_date) / 365
    )
  ELSE
    ROUND(
      (FLOOR(sc.total_amount / 4) / DATEDIFF(e.emp_resign, DATE_SUB(e.emp_resign, INTERVAL 3 MONTH)))
      * 30 * DATEDIFF(e.emp_resign, e.emp_hire_date) / 365
      * (1 - 0.05 - 0.05 * 0.10)
    )
  END AS net_amount,
  -- DC형: 70% 적립됐다 가정
  CASE WHEN e.retirement_type = 'DC' THEN
    ROUND(
      (FLOOR(sc.total_amount / 4) / DATEDIFF(e.emp_resign, DATE_SUB(e.emp_resign, INTERVAL 3 MONTH)))
      * 30 * DATEDIFF(e.emp_resign, e.emp_hire_date) / 365 * 0.7
    ) ELSE 0 END AS dc_deposited_total,
  CASE WHEN e.retirement_type = 'DC' THEN
    ROUND(
      (FLOOR(sc.total_amount / 4) / DATEDIFF(e.emp_resign, DATE_SUB(e.emp_resign, INTERVAL 3 MONTH)))
      * 30 * DATEDIFF(e.emp_resign, e.emp_hire_date) / 365 * 0.3
    ) ELSE 0 END AS dc_diff_amount,
  'PAID', NULL,
  DATE_ADD(e.emp_resign, INTERVAL 14 DAY) AS transfer_date,
  @actor_emp_id,
  TIMESTAMP(DATE_ADD(e.emp_resign, INTERVAL 7 DAY), '10:00:00') AS confirmed_at,
  @actor_emp_id,
  TIMESTAMP(DATE_ADD(e.emp_resign, INTERVAL 14 DAY), '14:00:00') AS paid_at,
  e.emp_name,
  d.dept_name,
  g.grade_name,
  wg.group_name,
  NOW(), NOW()
FROM employee e
JOIN salary_contract sc ON sc.emp_id = e.emp_id AND sc.company_id = @cid
JOIN department d ON d.dept_id = e.dept_id
JOIN grade      g ON g.grade_id = e.grade_id
LEFT JOIN work_group wg ON wg.work_group_id = e.work_group_id
WHERE e.company_id = @cid
  AND e.emp_status = 'RESIGNED'
  AND DATEDIFF(e.emp_resign, e.emp_hire_date) >= 365;   -- 1년 이상 근속자만 (법정퇴직금 요건)


-- =====================================================================
-- [검증 쿼리]
-- =====================================================================
-- vacation_balance 카운트
-- SELECT balance_year, COUNT(*) AS cnt, AVG(total_days) AS avg_total, AVG(used_days) AS avg_used
--   FROM vacation_balance WHERE company_id=@cid AND type_id=@annual_type_id
--  GROUP BY balance_year ORDER BY balance_year;
--
-- leave_allowance 분포
-- SELECT year, allowance_type, status, COUNT(*) AS cnt, SUM(allowance_amount) AS total_amount
--   FROM leave_allowance WHERE company_id=@cid
--  GROUP BY year, allowance_type, status ORDER BY year, allowance_type, status;
--
-- severance_pays 7건 상세
-- SELECT emp_name, dept_name, grade_name, retirement_type,
--        hire_date, resign_date, service_years, severance_amount, tax_amount, net_amount, sev_status
--   FROM severance_pays WHERE company_id=@cid ORDER BY resign_date;
