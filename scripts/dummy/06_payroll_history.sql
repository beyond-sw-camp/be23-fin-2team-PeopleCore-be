use peoplecore;
-- =====================================================================
-- HR Service 더미 급여이력 (PayrollRuns × 15 + EmpStatus + Details + PayStubs)
-- ---------------------------------------------------------------------
-- 선행 조건:
--   1) 02_hr_employees.sql      (사원 + resign)
--   2) 03_hr_salary_contracts.sql (연봉계약 + 4개 detail)
--   3) 05_overtime_requests.sql  (변동수당 산정의 기초)
--
-- [기간]
--   2025-01 ~ 2026-03  (15개월)
--   ※ 2026-04 는 발표 시연에서 추가근무 신청/승인 후 급여대장을 직접 생성
--
-- [상태]
--   모두 PAID 로 박음 (지급완료 이력).
--   payroll_runs.payroll_status = PAID, pay_date = 해당월 25일
--   payroll_emp_status.status = PAID, paid_at = 25일 14:00
--   pay_stubs.send_status = SENT
--
-- [대상 사원]
--   해당 월에 재직 중이었던 사원 (입사일 <= 월말 AND (퇴직일 IS NULL OR 퇴직일 >= 월첫일))
--
-- [지급 항목 — 사원당 매월]
--   고정: 기본급, 직책수당, 식대, 교통비   (salary_contract_detail 그대로 복사)
--         단, 퇴직월이면 PayrollService 와 동일하게 정액 항목 일할계산
--         = 월 금액 × (퇴직일까지 재직일수 / 해당월 일수)
--   변동: 연장근로수당, 야간근로수당, 휴일근로수당  (overtime_request 분 합산 × 시급 가산)
--
-- [공제 항목 — 단순 정률]
--   국민연금       : (기본급+직책수당) × 4.5%
--   건강보험       : (기본급+직책수당) × 3.545%
--   장기요양보험   : 건강보험 × 12.95%
--   고용보험       : (기본급+직책수당) × 0.9%
--   근로소득세     : (기본급+직책수당) × 5%      (간이세액표 단순화)
--   근로지방소득세 : 근로소득세 × 10%
--
-- [실행 방법]
--   $ mysql -u <user> -p peoplecore < 06_payroll_history.sql
-- =====================================================================

SET @company_name := 'peoplecore';
SET @cid := (SELECT company_id FROM company WHERE company_name = @company_name);
SET @start_ym := '2025-01';   -- 시작 yyyy-MM
SET @end_ym   := '2026-03';   -- 종료 yyyy-MM (포함)

-- 확정/지급 처리자: 인사팀장 (EMP-2025-005, 최도윤)
SET @actor_emp_id := (SELECT emp_id FROM employee WHERE company_id=@cid AND emp_num='EMP-2025-005');

-- ▼ pay_items lookup (회사 생성 시 자동 시드된 16개 항목) ▼
-- 지급
SET @pi_basic    := (SELECT pay_item_id FROM pay_items WHERE company_id=@cid AND pay_item_name='기본급');
SET @pi_pos      := (SELECT pay_item_id FROM pay_items WHERE company_id=@cid AND pay_item_name='직책수당');
SET @pi_meal     := (SELECT pay_item_id FROM pay_items WHERE company_id=@cid AND pay_item_name='식대');
SET @pi_trans    := (SELECT pay_item_id FROM pay_items WHERE company_id=@cid AND pay_item_name='교통비');
SET @pi_extended := (SELECT pay_item_id FROM pay_items WHERE company_id=@cid AND pay_item_name='연장근로수당');
SET @pi_night    := (SELECT pay_item_id FROM pay_items WHERE company_id=@cid AND pay_item_name='야간근로수당');
SET @pi_holiday  := (SELECT pay_item_id FROM pay_items WHERE company_id=@cid AND pay_item_name='휴일근로수당');
-- 공제
SET @pi_inc_tax  := (SELECT pay_item_id FROM pay_items WHERE company_id=@cid AND pay_item_name='근로소득세');
SET @pi_loc_tax  := (SELECT pay_item_id FROM pay_items WHERE company_id=@cid AND pay_item_name='근로지방소득세');
SET @pi_pension  := (SELECT pay_item_id FROM pay_items WHERE company_id=@cid AND pay_item_name='국민연금');
SET @pi_health   := (SELECT pay_item_id FROM pay_items WHERE company_id=@cid AND pay_item_name='건강보험');
SET @pi_longterm := (SELECT pay_item_id FROM pay_items WHERE company_id=@cid AND pay_item_name='장기요양보험');
SET @pi_employ   := (SELECT pay_item_id FROM pay_items WHERE company_id=@cid AND pay_item_name='고용보험');

SELECT
  IFNULL(BIN_TO_UUID(@cid), '❌ 회사 없음') AS company,
  @actor_emp_id AS actor,
  @pi_basic AS pi_basic, @pi_pos AS pi_pos, @pi_meal AS pi_meal, @pi_trans AS pi_trans,
  @pi_extended AS pi_ext, @pi_night AS pi_night, @pi_holiday AS pi_hol,
  @pi_inc_tax AS pi_inc, @pi_loc_tax AS pi_loc, @pi_pension AS pi_pen,
  @pi_health AS pi_hlt, @pi_longterm AS pi_lt, @pi_employ AS pi_emp;


-- =====================================================================
-- ▼ 기존 시드 정리 (회사 한정) — 멱등 실행 보장 ▼
-- =====================================================================
DELETE pd FROM payroll_details pd
  JOIN payroll_runs pr ON pr.payroll_run_id = pd.payroll_run_id
 WHERE pr.company_id = @cid
   AND pr.pay_year_month BETWEEN @start_ym AND @end_ym;

DELETE ps FROM pay_stubs ps
 WHERE ps.company_id = @cid
   AND ps.pay_year_month BETWEEN @start_ym AND @end_ym;

DELETE pes FROM payroll_emp_status pes
  JOIN payroll_runs pr ON pr.payroll_run_id = pes.payroll_run_id
 WHERE pr.company_id = @cid
   AND pr.pay_year_month BETWEEN @start_ym AND @end_ym;

DELETE FROM payroll_runs
 WHERE company_id = @cid
   AND pay_year_month BETWEEN @start_ym AND @end_ym;


-- =====================================================================
-- 1) payroll_runs × 15건 (2025-01 ~ 2026-03)
-- ---------------------------------------------------------------------
--   pay_date = 해당월 25일 (회사 일반 지급일 가정)
--   합계 컬럼은 0 으로 박았다가 마지막 단계에서 UPDATE
-- =====================================================================

INSERT INTO payroll_runs (
  company_id, pay_year_month, payroll_status,
  total_employees, total_pay, total_deduction, total_net_pay,
  pay_date, approval_doc_id, total_industrial_accident
)
SELECT
  @cid,
  DATE_FORMAT(DATE_ADD(STR_TO_DATE(CONCAT(@start_ym, '-01'), '%Y-%m-%d'),
                       INTERVAL t.n MONTH), '%Y-%m'),
  'PAID',
  0, 0, 0, 0,
  DATE(CONCAT(DATE_FORMAT(DATE_ADD(STR_TO_DATE(CONCAT(@start_ym, '-01'), '%Y-%m-%d'),
                                   INTERVAL t.n MONTH), '%Y-%m'), '-25')),
  NULL, NULL
FROM (
  SELECT 0 AS n UNION ALL SELECT 1  UNION ALL SELECT 2  UNION ALL SELECT 3
  UNION ALL SELECT 4 UNION ALL SELECT 5  UNION ALL SELECT 6  UNION ALL SELECT 7
  UNION ALL SELECT 8 UNION ALL SELECT 9  UNION ALL SELECT 10 UNION ALL SELECT 11
  UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15
) t;


-- =====================================================================
-- 2) payroll_emp_status — 사원 × payroll_run (그 월에 재직 중인 사원만)
-- ---------------------------------------------------------------------
--   재직조건: emp_hire_date <= 월말 AND (emp_resign IS NULL OR emp_resign >= 월첫일)
--   상태: PAID (전부 지급완료 시나리오)
--   confirmed_at = 해당월 22일 11:00, paid_at = 25일 14:00
-- =====================================================================

INSERT INTO payroll_emp_status (
  payroll_run_id, emp_id, company_id, status,
  confirmed_at, confirmed_by, approval_doc_id, paid_at
)
SELECT
  pr.payroll_run_id, e.emp_id, @cid, 'PAID',
  TIMESTAMP(DATE(CONCAT(pr.pay_year_month, '-22')), '11:00:00'),
  @actor_emp_id,
  NULL,
  TIMESTAMP(DATE(CONCAT(pr.pay_year_month, '-25')), '14:00:00')
FROM payroll_runs pr
CROSS JOIN employee e
WHERE pr.company_id = @cid
  AND pr.pay_year_month BETWEEN @start_ym AND @end_ym
  AND e.company_id = @cid
  AND e.emp_hire_date <= LAST_DAY(STR_TO_DATE(CONCAT(pr.pay_year_month, '-01'), '%Y-%m-%d'))
  AND (e.emp_resign IS NULL
       OR e.emp_resign >= STR_TO_DATE(CONCAT(pr.pay_year_month, '-01'), '%Y-%m-%d'));


-- =====================================================================
-- 3) payroll_details — 고정 지급 4항목 (salary_contract_detail 그대로 복사)
-- ---------------------------------------------------------------------
--   pay_item_name 은 NOT NULL 스냅샷 컬럼이라 pay_items 에서 가져옴
--   is_overtime_pay = FALSE
--   퇴직월: 정액 항목 일할계산 (예: 2025-03-15 퇴직 → 15/31)
-- =====================================================================

INSERT INTO payroll_details (
  payroll_run_id, emp_id, company_id, pay_item_id,
  amount, pay_item_name, pay_item_type, is_overtime_pay
)
SELECT
  pes.payroll_run_id, pes.emp_id, @cid, scd.pay_item_id,
  ROUND(
    scd.amount *
    CASE
      WHEN e.emp_resign IS NOT NULL
       AND DATE_FORMAT(e.emp_resign, '%Y-%m') = pr.pay_year_month
      THEN DAY(e.emp_resign) / DAY(LAST_DAY(STR_TO_DATE(CONCAT(pr.pay_year_month, '-01'), '%Y-%m-%d')))
      ELSE 1
    END
  ),
  pi.pay_item_name, 'PAYMENT', FALSE
FROM payroll_emp_status pes
JOIN payroll_runs pr ON pr.payroll_run_id = pes.payroll_run_id
JOIN employee e ON e.emp_id = pes.emp_id AND e.company_id = @cid
JOIN salary_contract sc ON sc.emp_id = pes.emp_id AND sc.company_id = @cid
JOIN salary_contract_detail scd ON scd.contract_id = sc.contract_id
JOIN pay_items pi ON pi.pay_item_id = scd.pay_item_id
WHERE pes.company_id = @cid
  AND pr.pay_year_month BETWEEN @start_ym AND @end_ym;


-- =====================================================================
-- 4) payroll_details — 변동지급 3항목 (overtime_request 분 합산)
-- ---------------------------------------------------------------------
--   계산식 (PayrollService.java 라인 998-1006 동일):
--     hourly_wage   = FLOOR(total_amount / 12 / 209)
--     extendedPay   = ROUND(hourly_wage * 1.5 * SUM(ext_min)  / 60)
--     nightPay      = ROUND(hourly_wage * 0.5 * SUM(night_min)/ 60)   -- 가산분 0.5만
--     holidayPay    = ROUND(hourly_wage * 1.5 * SUM(hol_min)  / 60)   -- 8h초과는 단순화
--
--   카테고리별 분 수 (overtime_request 시간으로 결정):
--     A: 19:00~22:00 → ext=180, night=0,   hol=0
--     B: 22:00~익02:00 → ext=240, night=240, hol=0   (모두 야간 가산 대상)
--     C: 토 09:00~17:00 → ext=0,  night=0,   hol=480
--   is_overtime_pay = TRUE
-- =====================================================================

-- 4-1. 연장근로수당
INSERT INTO payroll_details (
  payroll_run_id, emp_id, company_id, pay_item_id,
  amount, pay_item_name, pay_item_type, is_overtime_pay
)
SELECT
  pr.payroll_run_id, ot.emp_id, @cid, @pi_extended,
  ROUND(hw.hourly_wage * 1.5 * SUM(
    CASE WHEN HOUR(ot.ot_plan_start) = 19 THEN 180
         WHEN HOUR(ot.ot_plan_start) = 22 THEN 240
         ELSE 0
    END
  ) / 60.0),
  '연장근로수당', 'PAYMENT', TRUE
FROM overtime_request ot
JOIN payroll_runs pr
  ON pr.pay_year_month = DATE_FORMAT(ot.ot_date, '%Y-%m')
 AND pr.company_id = @cid
JOIN (
  SELECT sc.emp_id, FLOOR(sc.total_amount / 12 / 209) AS hourly_wage
    FROM salary_contract sc WHERE sc.company_id = @cid
) hw ON hw.emp_id = ot.emp_id
JOIN payroll_emp_status pes
  ON pes.payroll_run_id = pr.payroll_run_id AND pes.emp_id = ot.emp_id
WHERE ot.company_id = @cid
  AND ot.ot_status = 'APPROVED'
  AND pr.pay_year_month BETWEEN @start_ym AND @end_ym
GROUP BY pr.payroll_run_id, ot.emp_id, hw.hourly_wage
HAVING SUM(CASE WHEN HOUR(ot.ot_plan_start) IN (19, 22) THEN 1 ELSE 0 END) > 0;

-- 4-2. 야간근로수당
INSERT INTO payroll_details (
  payroll_run_id, emp_id, company_id, pay_item_id,
  amount, pay_item_name, pay_item_type, is_overtime_pay
)
SELECT
  pr.payroll_run_id, ot.emp_id, @cid, @pi_night,
  ROUND(hw.hourly_wage * 0.5 * SUM(
    CASE WHEN HOUR(ot.ot_plan_start) = 22 THEN 240 ELSE 0 END
  ) / 60.0),
  '야간근로수당', 'PAYMENT', TRUE
FROM overtime_request ot
JOIN payroll_runs pr
  ON pr.pay_year_month = DATE_FORMAT(ot.ot_date, '%Y-%m')
 AND pr.company_id = @cid
JOIN (
  SELECT sc.emp_id, FLOOR(sc.total_amount / 12 / 209) AS hourly_wage
    FROM salary_contract sc WHERE sc.company_id = @cid
) hw ON hw.emp_id = ot.emp_id
JOIN payroll_emp_status pes
  ON pes.payroll_run_id = pr.payroll_run_id AND pes.emp_id = ot.emp_id
WHERE ot.company_id = @cid
  AND ot.ot_status = 'APPROVED'
  AND pr.pay_year_month BETWEEN @start_ym AND @end_ym
GROUP BY pr.payroll_run_id, ot.emp_id, hw.hourly_wage
HAVING SUM(CASE WHEN HOUR(ot.ot_plan_start) = 22 THEN 1 ELSE 0 END) > 0;

-- 4-3. 휴일근로수당
INSERT INTO payroll_details (
  payroll_run_id, emp_id, company_id, pay_item_id,
  amount, pay_item_name, pay_item_type, is_overtime_pay
)
SELECT
  pr.payroll_run_id, ot.emp_id, @cid, @pi_holiday,
  ROUND(hw.hourly_wage * 1.5 * SUM(
    CASE WHEN HOUR(ot.ot_plan_start) = 9 THEN 480 ELSE 0 END
  ) / 60.0),
  '휴일근로수당', 'PAYMENT', TRUE
FROM overtime_request ot
JOIN payroll_runs pr
  ON pr.pay_year_month = DATE_FORMAT(ot.ot_date, '%Y-%m')
 AND pr.company_id = @cid
JOIN (
  SELECT sc.emp_id, FLOOR(sc.total_amount / 12 / 209) AS hourly_wage
    FROM salary_contract sc WHERE sc.company_id = @cid
) hw ON hw.emp_id = ot.emp_id
JOIN payroll_emp_status pes
  ON pes.payroll_run_id = pr.payroll_run_id AND pes.emp_id = ot.emp_id
WHERE ot.company_id = @cid
  AND ot.ot_status = 'APPROVED'
  AND pr.pay_year_month BETWEEN @start_ym AND @end_ym
GROUP BY pr.payroll_run_id, ot.emp_id, hw.hourly_wage
HAVING SUM(CASE WHEN HOUR(ot.ot_plan_start) = 9 THEN 1 ELSE 0 END) > 0;


-- =====================================================================
-- 5) payroll_details — 공제 6항목 (정률 단순화)
-- ---------------------------------------------------------------------
--   기준: 기본급 + 직책수당 (식대/교통비 비과세, 변동수당은 단순화 위해 제외)
--   퇴직월: 일할계산된 기본급+직책수당을 기준으로 공제 산정
-- =====================================================================

-- 사원별 (기본급+직책수당) 합 임시 view (각 INSERT 마다 동일하므로 Subquery 로 처리)

-- 5-1. 국민연금 (4.5%)
INSERT INTO payroll_details (
  payroll_run_id, emp_id, company_id, pay_item_id,
  amount, pay_item_name, pay_item_type, is_overtime_pay
)
SELECT
  pes.payroll_run_id, pes.emp_id, @cid, @pi_pension,
  ROUND(
    (scd_b.amount + scd_p.amount) *
    CASE
      WHEN e.emp_resign IS NOT NULL
       AND DATE_FORMAT(e.emp_resign, '%Y-%m') = pr.pay_year_month
      THEN DAY(e.emp_resign) / DAY(LAST_DAY(STR_TO_DATE(CONCAT(pr.pay_year_month, '-01'), '%Y-%m-%d')))
      ELSE 1
    END *
    0.045
  ),
  '국민연금', 'DEDUCTION', FALSE
FROM payroll_emp_status pes
JOIN payroll_runs pr ON pr.payroll_run_id = pes.payroll_run_id
JOIN employee e ON e.emp_id = pes.emp_id AND e.company_id = @cid
JOIN salary_contract sc ON sc.emp_id = pes.emp_id AND sc.company_id = @cid
JOIN salary_contract_detail scd_b ON scd_b.contract_id = sc.contract_id AND scd_b.pay_item_id = @pi_basic
JOIN salary_contract_detail scd_p ON scd_p.contract_id = sc.contract_id AND scd_p.pay_item_id = @pi_pos
WHERE pes.company_id = @cid AND pr.pay_year_month BETWEEN @start_ym AND @end_ym;

-- 5-2. 건강보험 (3.545%)
INSERT INTO payroll_details (
  payroll_run_id, emp_id, company_id, pay_item_id,
  amount, pay_item_name, pay_item_type, is_overtime_pay
)
SELECT
  pes.payroll_run_id, pes.emp_id, @cid, @pi_health,
  ROUND(
    (scd_b.amount + scd_p.amount) *
    CASE
      WHEN e.emp_resign IS NOT NULL
       AND DATE_FORMAT(e.emp_resign, '%Y-%m') = pr.pay_year_month
      THEN DAY(e.emp_resign) / DAY(LAST_DAY(STR_TO_DATE(CONCAT(pr.pay_year_month, '-01'), '%Y-%m-%d')))
      ELSE 1
    END *
    0.03545
  ),
  '건강보험', 'DEDUCTION', FALSE
FROM payroll_emp_status pes
JOIN payroll_runs pr ON pr.payroll_run_id = pes.payroll_run_id
JOIN employee e ON e.emp_id = pes.emp_id AND e.company_id = @cid
JOIN salary_contract sc ON sc.emp_id = pes.emp_id AND sc.company_id = @cid
JOIN salary_contract_detail scd_b ON scd_b.contract_id = sc.contract_id AND scd_b.pay_item_id = @pi_basic
JOIN salary_contract_detail scd_p ON scd_p.contract_id = sc.contract_id AND scd_p.pay_item_id = @pi_pos
WHERE pes.company_id = @cid AND pr.pay_year_month BETWEEN @start_ym AND @end_ym;

-- 5-3. 장기요양보험 (건강보험 × 12.95%)
INSERT INTO payroll_details (
  payroll_run_id, emp_id, company_id, pay_item_id,
  amount, pay_item_name, pay_item_type, is_overtime_pay
)
SELECT
  pes.payroll_run_id, pes.emp_id, @cid, @pi_longterm,
  ROUND(
    (scd_b.amount + scd_p.amount) *
    CASE
      WHEN e.emp_resign IS NOT NULL
       AND DATE_FORMAT(e.emp_resign, '%Y-%m') = pr.pay_year_month
      THEN DAY(e.emp_resign) / DAY(LAST_DAY(STR_TO_DATE(CONCAT(pr.pay_year_month, '-01'), '%Y-%m-%d')))
      ELSE 1
    END *
    0.03545 * 0.1295
  ),
  '장기요양보험', 'DEDUCTION', FALSE
FROM payroll_emp_status pes
JOIN payroll_runs pr ON pr.payroll_run_id = pes.payroll_run_id
JOIN employee e ON e.emp_id = pes.emp_id AND e.company_id = @cid
JOIN salary_contract sc ON sc.emp_id = pes.emp_id AND sc.company_id = @cid
JOIN salary_contract_detail scd_b ON scd_b.contract_id = sc.contract_id AND scd_b.pay_item_id = @pi_basic
JOIN salary_contract_detail scd_p ON scd_p.contract_id = sc.contract_id AND scd_p.pay_item_id = @pi_pos
WHERE pes.company_id = @cid AND pr.pay_year_month BETWEEN @start_ym AND @end_ym;

-- 5-4. 고용보험 (0.9%)
INSERT INTO payroll_details (
  payroll_run_id, emp_id, company_id, pay_item_id,
  amount, pay_item_name, pay_item_type, is_overtime_pay
)
SELECT
  pes.payroll_run_id, pes.emp_id, @cid, @pi_employ,
  ROUND(
    (scd_b.amount + scd_p.amount) *
    CASE
      WHEN e.emp_resign IS NOT NULL
       AND DATE_FORMAT(e.emp_resign, '%Y-%m') = pr.pay_year_month
      THEN DAY(e.emp_resign) / DAY(LAST_DAY(STR_TO_DATE(CONCAT(pr.pay_year_month, '-01'), '%Y-%m-%d')))
      ELSE 1
    END *
    0.009
  ),
  '고용보험', 'DEDUCTION', FALSE
FROM payroll_emp_status pes
JOIN payroll_runs pr ON pr.payroll_run_id = pes.payroll_run_id
JOIN employee e ON e.emp_id = pes.emp_id AND e.company_id = @cid
JOIN salary_contract sc ON sc.emp_id = pes.emp_id AND sc.company_id = @cid
JOIN salary_contract_detail scd_b ON scd_b.contract_id = sc.contract_id AND scd_b.pay_item_id = @pi_basic
JOIN salary_contract_detail scd_p ON scd_p.contract_id = sc.contract_id AND scd_p.pay_item_id = @pi_pos
WHERE pes.company_id = @cid AND pr.pay_year_month BETWEEN @start_ym AND @end_ym;

-- 5-5. 근로소득세 (5%, 간이세액표 단순화)
INSERT INTO payroll_details (
  payroll_run_id, emp_id, company_id, pay_item_id,
  amount, pay_item_name, pay_item_type, is_overtime_pay
)
SELECT
  pes.payroll_run_id, pes.emp_id, @cid, @pi_inc_tax,
  ROUND(
    (scd_b.amount + scd_p.amount) *
    CASE
      WHEN e.emp_resign IS NOT NULL
       AND DATE_FORMAT(e.emp_resign, '%Y-%m') = pr.pay_year_month
      THEN DAY(e.emp_resign) / DAY(LAST_DAY(STR_TO_DATE(CONCAT(pr.pay_year_month, '-01'), '%Y-%m-%d')))
      ELSE 1
    END *
    0.05
  ),
  '근로소득세', 'DEDUCTION', FALSE
FROM payroll_emp_status pes
JOIN payroll_runs pr ON pr.payroll_run_id = pes.payroll_run_id
JOIN employee e ON e.emp_id = pes.emp_id AND e.company_id = @cid
JOIN salary_contract sc ON sc.emp_id = pes.emp_id AND sc.company_id = @cid
JOIN salary_contract_detail scd_b ON scd_b.contract_id = sc.contract_id AND scd_b.pay_item_id = @pi_basic
JOIN salary_contract_detail scd_p ON scd_p.contract_id = sc.contract_id AND scd_p.pay_item_id = @pi_pos
WHERE pes.company_id = @cid AND pr.pay_year_month BETWEEN @start_ym AND @end_ym;

-- 5-6. 근로지방소득세 (근로소득세 × 10%)
INSERT INTO payroll_details (
  payroll_run_id, emp_id, company_id, pay_item_id,
  amount, pay_item_name, pay_item_type, is_overtime_pay
)
SELECT
  pes.payroll_run_id, pes.emp_id, @cid, @pi_loc_tax,
  ROUND(
    (scd_b.amount + scd_p.amount) *
    CASE
      WHEN e.emp_resign IS NOT NULL
       AND DATE_FORMAT(e.emp_resign, '%Y-%m') = pr.pay_year_month
      THEN DAY(e.emp_resign) / DAY(LAST_DAY(STR_TO_DATE(CONCAT(pr.pay_year_month, '-01'), '%Y-%m-%d')))
      ELSE 1
    END *
    0.05 * 0.10
  ),
  '근로지방소득세', 'DEDUCTION', FALSE
FROM payroll_emp_status pes
JOIN payroll_runs pr ON pr.payroll_run_id = pes.payroll_run_id
JOIN employee e ON e.emp_id = pes.emp_id AND e.company_id = @cid
JOIN salary_contract sc ON sc.emp_id = pes.emp_id AND sc.company_id = @cid
JOIN salary_contract_detail scd_b ON scd_b.contract_id = sc.contract_id AND scd_b.pay_item_id = @pi_basic
JOIN salary_contract_detail scd_p ON scd_p.contract_id = sc.contract_id AND scd_p.pay_item_id = @pi_pos
WHERE pes.company_id = @cid AND pr.pay_year_month BETWEEN @start_ym AND @end_ym;


-- =====================================================================
-- 6) pay_stubs — 사원 × payroll_run 별 1행 (총액은 payroll_details SUM)
-- ---------------------------------------------------------------------
--   send_status = SENT, issued_at = 25일 14:00, sent_at = 25일 16:00
-- =====================================================================

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
-- 7) payroll_runs 합계 갱신 (PayrollService.updateTotals 와 동일)
-- ---------------------------------------------------------------------
--   total_employees = COUNT(payroll_emp_status)
--   total_pay       = SUM(payroll_details PAYMENT)
--   total_deduction = SUM(payroll_details DEDUCTION)
--   total_net_pay   = total_pay - total_deduction
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
-- 월별 payroll_runs 합계
-- SELECT pay_year_month, total_employees, total_pay, total_deduction, total_net_pay, payroll_status, pay_date
--   FROM payroll_runs WHERE company_id=@cid ORDER BY pay_year_month;
--
-- 월별 사원 카운트 (재직자 변동 확인 — 116~120 퇴직 시점에 감소 보여야 함)
-- SELECT pr.pay_year_month, COUNT(*) AS emp_cnt
--   FROM payroll_emp_status pes JOIN payroll_runs pr ON pr.payroll_run_id = pes.payroll_run_id
--  WHERE pr.company_id=@cid GROUP BY pr.pay_year_month ORDER BY pr.pay_year_month;
--
-- 한 사원의 한 달 명세 (예: emp_id=42, 2025-11)
-- SELECT pi.pay_item_name, pd.amount, pd.pay_item_type, pd.is_overtime_pay
--   FROM payroll_details pd
--   JOIN pay_items pi ON pi.pay_item_id = pd.pay_item_id
--   JOIN payroll_runs pr ON pr.payroll_run_id = pd.payroll_run_id
--  WHERE pr.company_id=@cid AND pr.pay_year_month='2025-11' AND pd.emp_id=42
--  ORDER BY pd.pay_item_type, pi.pay_item_name;
--
-- 변동수당 발생 사원 비율 확인
-- SELECT pi.pay_item_name, COUNT(*) AS rows_, SUM(pd.amount) AS total
--   FROM payroll_details pd
--   JOIN pay_items pi ON pi.pay_item_id = pd.pay_item_id
--  WHERE pi.pay_item_name IN ('연장근로수당','야간근로수당','휴일근로수당')
--  GROUP BY pi.pay_item_name;
