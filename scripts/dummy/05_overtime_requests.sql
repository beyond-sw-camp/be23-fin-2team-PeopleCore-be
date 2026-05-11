use peoplecore;
-- =====================================================================
-- HR Service 더미 초과근무 신청 (OvertimeRequest × N건)
-- ---------------------------------------------------------------------
-- 선행 조건:
--   1) 02_hr_employees.sql 실행 완료
--   2) 06_payroll_history.sql 보다 먼저 실행돼야 함 (변동수당 산정의 기초)
--
-- [기간]
--   2025-01-01 ~ 2026-03-31  (15개월 연속)
--   ※ 2026-04 는 발표 시연에서 직접 추가근무 신청/승인 후 급여대장 생성
--
-- [3가지 카테고리 — 시간 패턴으로 분 수 결정 가능하게 고정]
--   Cat A: 평일 19:00~22:00  → 180분 = 모두 연장근로 (야간 0)
--   Cat B: 평일 22:00~다음02:00 → 240분 = 연장 + 야간 모두 (22:00~06:00 = 야간)
--   Cat C: 토요일 09:00~17:00 → 480분 = 모두 휴일근로
--
-- [분포 — emp_id × work_date CRC32 결정론적]
--   bucket = CRC32(CONCAT(emp_id, work_date)) % 100
--   평일: bucket <  12          → Cat A   (월 약 2~3회 발생)
--         bucket BETWEEN 12,14  → Cat B   (월 약 0~1회)
--   토요일:bucket <  5           → Cat C   (월 약 0~1회)
--   그 외: 신청 없음
--
-- [상태]
--   전부 APPROVED. ot_status='APPROVED', manager_id=인사팀장(EMP-2025-005),
--   approval_doc_id=NULL.
--
-- [실행 방법]
--   $ mysql -u <user> -p peoplecore < 05_overtime_requests.sql
-- =====================================================================

SET @company_name := 'peoplecore';
SET @cid := (SELECT company_id FROM company WHERE company_name = @company_name);
SET @start_date := DATE('2025-01-01');
SET @end_date   := DATE('2026-03-31');

-- 승인자: 인사팀장 (EMP-2025-005, 최도윤)
SET @manager_id := (SELECT emp_id FROM employee WHERE company_id=@cid AND emp_num='EMP-2025-005');

SELECT
  IFNULL(BIN_TO_UUID(@cid), CONCAT('❌ 회사를 찾을 수 없습니다: ', @company_name)) AS resolved_company,
  @manager_id AS manager_id;


-- ▼ 기존 시드 정리 (회사 한정) ▼
DELETE FROM overtime_request
 WHERE company_id = @cid
   AND ot_date BETWEEN TIMESTAMP(@start_date, '00:00:00')
                   AND TIMESTAMP(@end_date,   '23:59:59');


-- =====================================================================
-- INSERT — 단일 INSERT-SELECT (derived 날짜 시퀀스 × 사원 × 카테고리 분기)
-- ---------------------------------------------------------------------
--   숫자 시퀀스: 0~9, 0~9, 0~4 cross join → 0~499
--   work_date  : @start_date + n DAY, BETWEEN 필터로 15개월 범위 정확히
--   사원       : 입사일 <= work_date AND (퇴직일 IS NULL OR work_date < 퇴직일)
--                emp_status IN (ACTIVE, ON_LEAVE) 만 (RESIGNED 는 퇴직 후 신청 불가)
-- =====================================================================

INSERT INTO overtime_request (
  company_id, emp_id, ot_date, ot_plan_start, ot_plan_end,
  ot_reason, ot_status, manager_id, approval_doc_id, version,
  created_at, updated_at
)
SELECT
  r.company_id,
  r.emp_id,
  TIMESTAMP(r.work_date, '00:00:00') AS ot_date,
  -- ot_plan_start (카테고리별 시작 시각)
  CASE r.cat
    WHEN 'A' THEN TIMESTAMP(r.work_date, '19:00:00')
    WHEN 'B' THEN TIMESTAMP(r.work_date, '22:00:00')
    WHEN 'C' THEN TIMESTAMP(r.work_date, '09:00:00')
  END AS ot_plan_start,
  -- ot_plan_end (카테고리별 종료 시각)
  CASE r.cat
    WHEN 'A' THEN TIMESTAMP(r.work_date, '22:00:00')                              -- 19~22 (3시간)
    WHEN 'B' THEN TIMESTAMP(DATE_ADD(r.work_date, INTERVAL 1 DAY), '02:00:00')    -- 22~익일 02 (4시간)
    WHEN 'C' THEN TIMESTAMP(r.work_date, '17:00:00')                              -- 토 9~17 (8시간)
  END AS ot_plan_end,
  CASE r.cat
    WHEN 'A' THEN '월말 마감 작업'
    WHEN 'B' THEN '긴급 장애 대응'
    WHEN 'C' THEN '주말 정기 점검'
  END AS ot_reason,
  'APPROVED', @manager_id, NULL, 0,
  NOW(), NOW()
FROM (
  SELECT
    nums.work_date,
    e.company_id,
    e.emp_id,
    -- bucket = CRC32(emp_id + work_date) % 100
    (CRC32(CONCAT(e.emp_id, nums.work_date)) % 100) AS bucket,
    -- 카테고리 분류
    CASE
      WHEN WEEKDAY(nums.work_date) < 5
           AND (CRC32(CONCAT(e.emp_id, nums.work_date)) % 100) < 12  THEN 'A'
      WHEN WEEKDAY(nums.work_date) < 5
           AND (CRC32(CONCAT(e.emp_id, nums.work_date)) % 100) BETWEEN 12 AND 14 THEN 'B'
      WHEN WEEKDAY(nums.work_date) = 5
           AND (CRC32(CONCAT(e.emp_id, nums.work_date)) % 100) < 5   THEN 'C'
      ELSE NULL
    END AS cat
  FROM (
    -- 0~499 숫자 시퀀스 → @start_date + n 일
    SELECT DATE_ADD(@start_date, INTERVAL (u.n + t.n*10 + h.n*100) DAY) AS work_date
    FROM      (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
               UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) u
    CROSS JOIN (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
                UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) t
    CROSS JOIN (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4) h
  ) nums
  CROSS JOIN employee e
  WHERE e.company_id = @cid
    AND nums.work_date BETWEEN @start_date AND @end_date
    -- 사원이 그 시점에 재직 중이어야 함 (입사 후 ~ 퇴직 전)
    AND e.emp_hire_date <= nums.work_date
    AND (e.emp_resign IS NULL OR nums.work_date < e.emp_resign)
    -- 퇴직자/휴직자 제외 가능. 단 ON_LEAVE 도 과거 신청 이력 가능하므로 RESIGNED 만 명시 제외
    AND e.emp_status <> 'RESIGNED'
) r
WHERE r.cat IS NOT NULL;


-- =====================================================================
-- [검증 쿼리]
-- =====================================================================
-- 카테고리별 카운트
-- SELECT
--   CASE
--     WHEN HOUR(ot_plan_start) = 19 THEN 'A (평일 연장)'
--     WHEN HOUR(ot_plan_start) = 22 THEN 'B (평일 야간)'
--     WHEN HOUR(ot_plan_start) =  9 THEN 'C (토 휴일)'
--   END AS category,
--   COUNT(*) AS cnt
-- FROM overtime_request
-- WHERE company_id = @cid
-- GROUP BY category;
--
-- 월별 발생 건수
-- SELECT DATE_FORMAT(ot_date, '%Y-%m') AS ym, COUNT(*) AS cnt
--   FROM overtime_request WHERE company_id = @cid
--  GROUP BY ym ORDER BY ym;
--
-- 한 사원의 1개월 합산 분 (예: emp_id=42, 2025-11)
-- SELECT
--   SUM(CASE WHEN HOUR(ot_plan_start)=19 THEN 180
--            WHEN HOUR(ot_plan_start)=22 THEN 240 ELSE 0 END) AS ext_min,
--   SUM(CASE WHEN HOUR(ot_plan_start)=22 THEN 240 ELSE 0 END) AS night_min,
--   SUM(CASE WHEN HOUR(ot_plan_start)= 9 THEN 480 ELSE 0 END) AS hol_min
-- FROM overtime_request
-- WHERE company_id=@cid AND emp_id=42
--   AND ot_date BETWEEN '2025-11-01' AND '2025-11-30';
