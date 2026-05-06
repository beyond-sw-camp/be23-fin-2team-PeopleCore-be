use peoplecore;
-- =====================================================================
-- C 방안 후처리 — payroll_runs/emp_status 를 PAID 로 일괄 변경 + pay_stubs 생성
-- ---------------------------------------------------------------------
-- 선행 조건:
--   - seed_payroll_via_api.sh 실행 완료 (CALCULATING 상태로 16개월 산정 끝)
--
-- 처리 내용:
--   1) payroll_runs.payroll_status     = PAID,  pay_date = 25일
--   2) payroll_emp_status.status       = PAID,  confirmed_at/by + paid_at 시각 박음
--   3) pay_stubs                       = 사원×payroll_run 별 1행 (총액은 detail SUM)
--   4) payroll_runs 합계 컬럼 재계산 (안전 차원)
--
-- ※ 결재 상신/승인 단계는 외부 collaboration-service 의존이라 SQL 로 점프.
--   approval_doc_id 는 NULL 유지.
--
-- [실행 방법]
--   $ mysql -u <user> -p peoplecore < 06b_set_paid.sql
-- =====================================================================

SET @cid          := (SELECT company_id FROM company WHERE company_name='peoplecore');
SET @actor_emp_id := (SELECT emp_id FROM employee WHERE company_id=@cid AND emp_num='EMP-2025-005');
SET @start_ym     := '2025-01';
SET @end_ym       := '2026-04';

SELECT
  IFNULL(BIN_TO_UUID(@cid), '❌ 회사 없음') AS company,
  @actor_emp_id AS actor_emp_id,
  @start_ym AS start_ym, @end_ym AS end_ym;


-- =====================================================================
-- 1) payroll_runs → PAID + pay_date 25일
-- =====================================================================
UPDATE payroll_runs
   SET payroll_status = 'PAID',
       pay_date = DATE(CONCAT(pay_year_month, '-25'))
 WHERE company_id = @cid
   AND pay_year_month BETWEEN @start_ym AND @end_ym;


-- =====================================================================
-- 2) payroll_emp_status → PAID + confirmed/paid 시각
-- ---------------------------------------------------------------------
--   confirmed_at = 22일 11:00, paid_at = 25일 14:00
-- =====================================================================
UPDATE payroll_emp_status pes
  JOIN payroll_runs pr ON pr.payroll_run_id = pes.payroll_run_id
   SET pes.status       = 'PAID',
       pes.confirmed_at = TIMESTAMP(DATE(CONCAT(pr.pay_year_month, '-22')), '11:00:00'),
       pes.confirmed_by = @actor_emp_id,
       pes.paid_at      = TIMESTAMP(DATE(CONCAT(pr.pay_year_month, '-25')), '14:00:00')
 WHERE pr.company_id = @cid
   AND pr.pay_year_month BETWEEN @start_ym AND @end_ym;


-- =====================================================================
-- 3) pay_stubs — 사원 × payroll_run 별 1행 (재실행 멱등)
-- ---------------------------------------------------------------------
--   send_status = SENT, issued_at = 25일 14:00, sent_at = 25일 16:00
-- =====================================================================
DELETE ps FROM pay_stubs ps
 WHERE ps.company_id = @cid
   AND ps.pay_year_month BETWEEN @start_ym AND @end_ym;

INSERT INTO pay_stubs (
  payroll_run_id, emp_id, company_id, pay_year_month,
  total_pay, total_deduction, net_pay,
  send_status, sent_at, issued_at, pdf_url
)
SELECT
  pes.payroll_run_id, pes.emp_id, @cid, pr.pay_year_month,
  IFNULL((SELECT SUM(amount) FROM payroll_details pd
           WHERE pd.payroll_run_id = pes.payroll_run_id
             AND pd.emp_id = pes.emp_id
             AND pd.pay_item_type = 'PAYMENT'), 0),
  IFNULL((SELECT SUM(amount) FROM payroll_details pd
           WHERE pd.payroll_run_id = pes.payroll_run_id
             AND pd.emp_id = pes.emp_id
             AND pd.pay_item_type = 'DEDUCTION'), 0),
  IFNULL((SELECT SUM(CASE WHEN pd.pay_item_type='PAYMENT' THEN pd.amount ELSE -pd.amount END)
            FROM payroll_details pd
           WHERE pd.payroll_run_id = pes.payroll_run_id
             AND pd.emp_id = pes.emp_id), 0),
  'SENT',
  TIMESTAMP(DATE(CONCAT(pr.pay_year_month, '-25')), '16:00:00'),
  TIMESTAMP(DATE(CONCAT(pr.pay_year_month, '-25')), '14:00:00'),
  NULL
FROM payroll_emp_status pes
JOIN payroll_runs pr ON pr.payroll_run_id = pes.payroll_run_id
WHERE pes.company_id = @cid
  AND pr.pay_year_month BETWEEN @start_ym AND @end_ym;


-- =====================================================================
-- 4) payroll_runs 합계 재계산 (createPayroll 가 박은 값을 신뢰하지만 안전 차원)
-- =====================================================================
UPDATE payroll_runs pr
SET
  pr.total_employees = (
    SELECT COUNT(*) FROM payroll_emp_status pes WHERE pes.payroll_run_id = pr.payroll_run_id
  ),
  pr.total_pay = IFNULL((
    SELECT SUM(amount) FROM payroll_details pd
     WHERE pd.payroll_run_id = pr.payroll_run_id AND pd.pay_item_type = 'PAYMENT'
  ), 0),
  pr.total_deduction = IFNULL((
    SELECT SUM(amount) FROM payroll_details pd
     WHERE pd.payroll_run_id = pr.payroll_run_id AND pd.pay_item_type = 'DEDUCTION'
  ), 0)
WHERE pr.company_id = @cid
  AND pr.pay_year_month BETWEEN @start_ym AND @end_ym;

UPDATE payroll_runs
   SET total_net_pay = total_pay - total_deduction
 WHERE company_id = @cid
   AND pay_year_month BETWEEN @start_ym AND @end_ym;


-- =====================================================================
-- [검증 쿼리]
-- =====================================================================
-- 월별 status 카운트
-- SELECT pay_year_month, payroll_status, total_employees, total_pay, total_deduction, total_net_pay, pay_date
--   FROM payroll_runs WHERE company_id=@cid ORDER BY pay_year_month;
--
-- 사원별 status 카운트
-- SELECT pes.status, COUNT(*) FROM payroll_emp_status pes
--   JOIN payroll_runs pr ON pr.payroll_run_id = pes.payroll_run_id
--  WHERE pr.company_id = @cid GROUP BY pes.status;
-- 예상: PAID = 모든 (run, emp) 조합 수
--
-- pay_stubs 카운트 = payroll_emp_status 카운트와 일치해야
-- SELECT (SELECT COUNT(*) FROM pay_stubs WHERE company_id=@cid) AS stubs,
--        (SELECT COUNT(*) FROM payroll_emp_status pes
--           JOIN payroll_runs pr ON pr.payroll_run_id=pes.payroll_run_id
--          WHERE pr.company_id=@cid AND pr.pay_year_month BETWEEN @start_ym AND @end_ym) AS pes_cnt;
