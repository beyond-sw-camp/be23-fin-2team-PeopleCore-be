USE peoplecore;
-- =====================================================================
-- DC형 퇴직연금 월별 적립 시드 (2025-01 ~ 2026-03)
-- ---------------------------------------------------------------------
-- 선행 조건:
--   1) seed_payroll_via_api.sh : payroll_runs/payroll_details 15개월 생성
--   2) 06b_set_paid.sql        : 2025-01 ~ 2026-03 PAID 처리
--
-- 계산식 (PensionDepositService.createMonthlyDeposits 동일):
--   대상 사원   : Employee.retirement_type = 'DC'
--   baseAmount  : SUM(payroll_details.amount) WHERE pay_item_type = 'PAYMENT'
--                 (해당 emp×run 의 지급항목 합계 = 그 달의 총지급)
--   depositAmount = baseAmount / 12
--                 (annualized base 의 1/12 = 월 적립금)
--   depStatus     = 'COMPLETED'
--   depositDate   = pay_year_month-25 16:00:00 (PAID 시각 직후)
--   payroll_run_id= 해당 월 PAID payroll_run
--   pay_year_month= 'YYYY-MM'
--   is_manual     = 0
--
-- ※ 재실행 멱등: 기존 시드 행을 먼저 DELETE.
-- ※ 2026-04 는 발표 시연용으로 급여대장을 미리 생성하지 않아 적립 대상에서 제외됨.
--
-- [실행 방법]
--   $ mysql -u <user> -p peoplecore < 08_retirement_pension_deposits.sql
-- =====================================================================

SET @cid       := (SELECT company_id FROM company WHERE company_name='peoplecore');
SET @start_ym  := '2025-01';
SET @end_ym    := '2026-03';

SELECT
  IFNULL(BIN_TO_UUID(@cid), '❌ 회사 없음') AS company,
  @start_ym AS start_ym, @end_ym AS end_ym;


-- ---------------------------------------------------------------------
-- 0) 기존 시드 정리 (회사 한정, 2025-01 ~ 2026-03 만)
-- ---------------------------------------------------------------------
DELETE rpd FROM retirement_pension_deposits rpd
  JOIN payroll_runs pr ON pr.payroll_run_id = rpd.payroll_run_id
 WHERE rpd.company_id = @cid
   AND pr.pay_year_month BETWEEN @start_ym AND @end_ym;


-- ---------------------------------------------------------------------
-- 1) DC 사원 × PAID payroll_run 별 1행
-- ---------------------------------------------------------------------
INSERT INTO retirement_pension_deposits (
  emp_id, company_id, payroll_run_id, pay_year_month,
  base_amount, deposit_amount, deposit_date,
  dep_status, is_manual,
  reason, created_by, canceled_at, canceled_by
)
SELECT
  pes.emp_id,
  @cid,
  pes.payroll_run_id,
  pr.pay_year_month,
  IFNULL(SUM(pd.amount), 0)         AS base_amount,
  IFNULL(SUM(pd.amount), 0) DIV 12  AS deposit_amount,
  TIMESTAMP(DATE(CONCAT(pr.pay_year_month, '-25')), '16:00:00') AS deposit_date,
  'COMPLETED',
  0,
  NULL, NULL, NULL, NULL
FROM payroll_emp_status pes
JOIN payroll_runs   pr ON pr.payroll_run_id = pes.payroll_run_id
JOIN employee       e  ON e.emp_id = pes.emp_id
LEFT JOIN payroll_details pd
       ON pd.payroll_run_id = pes.payroll_run_id
      AND pd.emp_id         = pes.emp_id
      AND pd.pay_item_type  = 'PAYMENT'
WHERE pr.company_id = @cid
  AND pr.pay_year_month BETWEEN @start_ym AND @end_ym
  AND pr.payroll_status = 'PAID'
  AND e.retirement_type = 'DC'
GROUP BY pes.emp_id, pes.payroll_run_id, pr.pay_year_month;


-- =====================================================================
-- [검증 쿼리]
-- =====================================================================
-- A) 월별 DC 적립 건수 + 합계
SELECT pr.pay_year_month,
       COUNT(*)                AS dc_emp_cnt,
       SUM(rpd.base_amount)    AS sum_base,
       SUM(rpd.deposit_amount) AS sum_deposit,
       rpd.dep_status
  FROM retirement_pension_deposits rpd
  JOIN payroll_runs pr ON pr.payroll_run_id = rpd.payroll_run_id
 WHERE rpd.company_id=@cid
 GROUP BY pr.pay_year_month, rpd.dep_status
 ORDER BY pr.pay_year_month;

-- SELECT pr.pay_year_month,
--        COUNT(*)          AS dc_emp_cnt,
--        SUM(rpd.base_amount)    AS sum_base,
--        SUM(rpd.deposit_amount) AS sum_deposit,
--        rpd.dep_status
--   FROM retirement_pension_deposits rpd
--   JOIN payroll_runs pr ON pr.payroll_run_id = rpd.payroll_run_id
--  WHERE rpd.company_id=@cid
--  GROUP BY pr.pay_year_month, rpd.dep_status
--  ORDER BY pr.pay_year_month;
--
-- 예상: 매월 dep_status=COMPLETED, dc_emp_cnt = (그 달 PAID 대상 DC 사원 수)
-- 예상: deposit_amount = base_amount / 12 (정수 나눗셈)
--
-- B) 비DC 사원이 시드되었는지 검증 (없어야 정상)
-- SELECT COUNT(*) AS leaked
--   FROM retirement_pension_deposits rpd
--   JOIN employee e ON e.emp_id = rpd.emp_id
--  WHERE rpd.company_id=@cid AND e.retirement_type <> 'DC';
-- 예상: 0
--
-- C) 사원별 12개월 누적 적립금 샘플 (DC 사원 5명)
-- SELECT e.emp_num, e.emp_name, e.retirement_type,
--        COUNT(*) AS months,
--        SUM(rpd.deposit_amount) AS total_deposited
--   FROM retirement_pension_deposits rpd
--   JOIN employee e ON e.emp_id = rpd.emp_id
--   JOIN payroll_runs pr ON pr.payroll_run_id = rpd.payroll_run_id
--  WHERE rpd.company_id=@cid
--    AND e.retirement_type='DC'
--    AND pr.pay_year_month BETWEEN '2025-01' AND '2025-12'
--  GROUP BY e.emp_id ORDER BY e.emp_num LIMIT 5;
