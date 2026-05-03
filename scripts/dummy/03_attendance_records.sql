        use peoplecore;

        -- =====================================================================
        -- HR Service 더미 근태 데이터 (CommuteRecord 만)
        --   1) 사원 → 워크그룹 매핑 UPDATE
        --   2) commute_record 파티션 추가 (p202602 ~ p202605)
        --   3) CommuteRecord INSERT-SELECT (6개월 평일/토)
        -- ---------------------------------------------------------------------
        -- 선행 조건:
        --   - 회사 'peoplecore' + 01_hr_master_data.sql + 02_hr_employees.sql 실행 완료
        --   - commute_record 가 RANGE COLUMNS(work_date) 파티셔닝 + p202601 까지 존재
        --
        -- [실행 순서]
        --   1) Section 1 (워크그룹 매핑)
        --   2) Section 2 (파티션 추가) ← INSERT 전 필수
        --   3) Section 3 (CommuteRecord)
        --   4) Section 4 (OvertimeRequest)
        --   5) Section 5 (AttendanceModify)
        --
        -- [주의]
        --   - commute_record 파티션 형식이 TO_DAYS() 라면 Section 2 의 LESS THAN 값을
        --     TO_DAYS('YYYY-MM-DD') 로 바꿔야 함.
        --   - BaseTimeEntity Auditing 은 ORM 통과해야 작동 → 직접 SQL INSERT 시 NOW() 명시.
        --   - commute_record 의 unique key (company_id, emp_id, work_date) 로 중복 방지.
        -- =====================================================================

        -- ▼ 회사 + 워크그룹 + 부서 ID lookup ▼
        SET @company_name := 'peoplecore';
        SET @cid := (SELECT company_id FROM company WHERE company_name = @company_name);

        SET @wg_dev   := (SELECT work_group_id FROM work_group WHERE company_id=@cid AND group_code='WG-FLEX-DEV');
        SET @wg_sales := (SELECT work_group_id FROM work_group WHERE company_id=@cid AND group_code='WG-SALES-SAT');
        -- 자동 시드된 9-18 기본 그룹 (우리 추가 코드가 아닌 첫 번째 그룹) — group_code 가 우리 3개 외인 것
        SET @wg_std   := (SELECT work_group_id FROM work_group
                           WHERE company_id=@cid
                             AND group_code NOT IN ('WG-FLEX-DEV','WG-SALES-SAT','WG-SHORT')
                           ORDER BY work_group_id LIMIT 1);

        SET @d_dev    := (SELECT dept_id FROM department WHERE company_id=@cid AND dept_code='DEV');
        SET @d_sales  := (SELECT dept_id FROM department WHERE company_id=@cid AND dept_code='SALES');

        SELECT
          IFNULL(BIN_TO_UUID(@cid), '❌ 회사 없음') AS company,
          @wg_dev AS wg_dev, @wg_sales AS wg_sales, @wg_std AS wg_std,
          @d_dev AS d_dev, @d_sales AS d_sales;


        -- =====================================================================
        -- Section 1) 사원 → 워크그룹 매핑 UPDATE
        -- ---------------------------------------------------------------------
        --   개발팀 → WG-FLEX-DEV
        --   영업팀 → WG-SALES-SAT
        --   그 외   → 자동 시드된 기본 9-18 그룹
        -- =====================================================================

        UPDATE employee
           SET work_group_id = @wg_dev,
               work_group_assigned_at = NOW()
         WHERE company_id = @cid AND dept_id = @d_dev;

        UPDATE employee
           SET work_group_id = @wg_sales,
               work_group_assigned_at = NOW()
         WHERE company_id = @cid AND dept_id = @d_sales;

        UPDATE employee
           SET work_group_id = @wg_std,
               work_group_assigned_at = NOW()
         WHERE company_id = @cid AND work_group_id IS NULL;


        -- =====================================================================
        -- Section 2) commute_record 파티션 추가
        -- ---------------------------------------------------------------------
        -- 기존: p202601 (LESS THAN '2026-02-01') 까지
        -- 추가: p202602 ~ p202605 (2026-02 ~ 2026-05 데이터 수용)
        -- 2025-11/12, 2026-01 데이터는 p202601 (또는 그 이전 파티션) 에 들어감.
        -- =====================================================================

        ALTER TABLE commute_record ADD PARTITION (PARTITION p202602 VALUES LESS THAN ('2026-03-01'));
        ALTER TABLE commute_record ADD PARTITION (PARTITION p202603 VALUES LESS THAN ('2026-04-01'));
        ALTER TABLE commute_record ADD PARTITION (PARTITION p202604 VALUES LESS THAN ('2026-05-01'));
        ALTER TABLE commute_record ADD PARTITION (PARTITION p202605 VALUES LESS THAN ('2026-06-01'));


        -- =====================================================================
        -- Section 3) CommuteRecord 갈아끼우기 (DELETE → INSERT)
        -- ---------------------------------------------------------------------
        -- 기간: 오늘(CURDATE()) 기준 6개월 전 ~ 오늘 (실행 시점 동적)
        -- 대상: 입사~퇴직 사이의 모든 사원 (ACTIVE / ON_LEAVE / RESIGNED 무관)
        --       단, emp_hire_date 이후 + emp_resign 이전 일자만 포함
        -- 부서별 패턴:
        --   DEV    → 월~금 10:00-19:00 (그룹2)
        --   SALES  → 월~토 09:00-18:00 (그룹3)
        --   그 외  → 월~금 09:00-18:00 (자동 9-18)
        -- 분포: NORMAL 80 / LATE 9 / EARLY 4 / LATE_AND_EARLY 2 / ABSENT 3 / AUTO_CLOSED 2
        -- 결정론적 버킷: CRC32(emp_id || work_date) % 100
        -- =====================================================================

        -- ▼ 3-1) 기존 commute_record 전부 삭제 (회사 'peoplecore' 한정)
        DELETE FROM commute_record
         WHERE company_id = (SELECT company_id FROM company WHERE company_name = 'peoplecore');

        -- ▼ 3-2) 단일 INSERT-SELECT (변수/CTE 의존 없음, derived table 만 사용)
        --     - numbers 0~199 → DATE_SUB(CURDATE(), 6 MONTH) 부터 +n 일
        --     - WHERE work_date BETWEEN start AND end 로 정확한 6개월 범위 필터
        INSERT INTO commute_record (
          work_date, company_id, emp_id,
          com_rec_check_in, com_rec_check_out,
          check_in_ip, check_out_ip,
          holiday_reason, work_status,
          actual_work_minutes, overtime_minutes, unrecognized_ot_minutes,
          recognized_extended_minutes, recognized_night_minutes, recognized_holiday_minutes,
          created_at, updated_at
        )
        SELECT
          r.work_date, r.company_id, r.emp_id,
          -- com_rec_check_in
          CASE
            WHEN r.bucket BETWEEN 95 AND 97 THEN NULL
            WHEN r.pattern = 'DEV_FLEX' AND (r.bucket BETWEEN 80 AND 88 OR r.bucket BETWEEN 93 AND 94)
                 THEN TIMESTAMP(r.work_date, '10:15:00')
            WHEN r.pattern = 'DEV_FLEX'
                 THEN TIMESTAMP(r.work_date, '10:00:00')
            WHEN (r.bucket BETWEEN 80 AND 88 OR r.bucket BETWEEN 93 AND 94)
                 THEN TIMESTAMP(r.work_date, '09:15:00')
            ELSE TIMESTAMP(r.work_date, '09:00:00')
          END,
          -- com_rec_check_out
          CASE
            WHEN r.bucket >= 95 THEN NULL
            WHEN r.pattern = 'DEV_FLEX' AND (r.bucket BETWEEN 89 AND 92 OR r.bucket BETWEEN 93 AND 94)
                 THEN TIMESTAMP(r.work_date, '18:30:00')
            WHEN r.pattern = 'DEV_FLEX'
                 THEN TIMESTAMP(r.work_date, '19:00:00')
            WHEN (r.bucket BETWEEN 89 AND 92 OR r.bucket BETWEEN 93 AND 94)
                 THEN TIMESTAMP(r.work_date, '17:30:00')
            ELSE TIMESTAMP(r.work_date, '18:00:00')
          END,
          -- check_in_ip
          CASE WHEN r.bucket BETWEEN 95 AND 97 THEN NULL ELSE '192.168.0.10' END,
          -- check_out_ip
          CASE WHEN r.bucket >= 95 THEN NULL ELSE '192.168.0.10' END,
          -- holiday_reason
          NULL,
          -- work_status
          CASE
            WHEN r.bucket BETWEEN 95 AND 97 THEN 'ABSENT'
            WHEN r.bucket BETWEEN 98 AND 99 THEN 'AUTO_CLOSED'
            WHEN r.bucket BETWEEN 80 AND 88 THEN 'LATE'
            WHEN r.bucket BETWEEN 89 AND 92 THEN 'EARLY_LEAVE'
            WHEN r.bucket BETWEEN 93 AND 94 THEN 'LATE_AND_EARLY'
            ELSE 'NORMAL'
          END,
          -- actual_work_minutes
          CASE
            WHEN r.bucket >= 95 THEN 0
            WHEN r.bucket BETWEEN 80 AND 88 THEN 465
            WHEN r.bucket BETWEEN 89 AND 92 THEN 450
            WHEN r.bucket BETWEEN 93 AND 94 THEN 435
            ELSE 480
          END,
          0, 0, 0, 0, 0,
          NOW(), NOW()
        FROM (
          -- 사원 × 날짜 시리즈 + pattern + bucket 산출 (derived table)
          SELECT
            nums.work_date,
            e.company_id,
            e.emp_id,
            CASE WHEN d.dept_code = 'DEV'   THEN 'DEV_FLEX'
                 WHEN d.dept_code = 'SALES' THEN 'SALES_SAT'
                 ELSE 'STD'
            END AS pattern,
            (CRC32(CONCAT(e.emp_id, nums.work_date)) % 100) AS bucket
          FROM (
            -- 0~199 숫자 시퀀스 → DATE_SUB(CURDATE(), 6 MONTH) ~ +199일
            -- 이후 WHERE 로 work_date BETWEEN [6개월 전] AND CURDATE() 정확히 필터
            SELECT DATE_ADD(DATE_SUB(CURDATE(), INTERVAL 6 MONTH),
                            INTERVAL (u.n + t.n*10 + h.n*100) DAY) AS work_date
            FROM      (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
                       UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) u
            CROSS JOIN (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
                        UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) t
            CROSS JOIN (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2) h
            WHERE (u.n + t.n*10 + h.n*100) BETWEEN 0 AND 199
          ) nums
          CROSS JOIN employee e
          JOIN department d ON e.dept_id = d.dept_id
          WHERE e.company_id = (SELECT company_id FROM company WHERE company_name = 'peoplecore')
            -- 모든 사원 (ACTIVE/ON_LEAVE/RESIGNED 무관) — 단 입사~퇴직 사이만
            AND e.emp_hire_date <= nums.work_date
            AND (e.emp_resign IS NULL OR nums.work_date < e.emp_resign)
            -- 6개월 범위 정확히 필터
            AND nums.work_date BETWEEN DATE_SUB(CURDATE(), INTERVAL 6 MONTH) AND CURDATE()
            -- 평일 또는 영업팀 토요일만
            AND (
              WEEKDAY(nums.work_date) < 5
              OR (d.dept_code = 'SALES' AND WEEKDAY(nums.work_date) = 5)
            )
        ) r;


        -- =====================================================================
        -- [검증 쿼리] 실행 후 카운트 확인
        -- =====================================================================
        -- 사원 워크그룹 매핑
        -- SELECT wg.group_code, COUNT(*) AS emp_cnt
        --   FROM employee e
        --   LEFT JOIN work_group wg ON e.work_group_id = wg.work_group_id
        --  WHERE e.company_id = @cid
        --  GROUP BY wg.group_code;
        --
        -- CommuteRecord 분포
        -- SELECT work_status, COUNT(*) AS cnt
        --   FROM commute_record
        --  WHERE company_id = @cid AND work_date BETWEEN '2025-11-01' AND '2026-04-30'
        --  GROUP BY work_status;
        --
        -- 파티션 확인
        -- SELECT PARTITION_NAME, PARTITION_DESCRIPTION, TABLE_ROWS
        --   FROM INFORMATION_SCHEMA.PARTITIONS
        --  WHERE TABLE_SCHEMA='peoplecore' AND TABLE_NAME='commute_record'
        --  ORDER BY PARTITION_ORDINAL_POSITION;
