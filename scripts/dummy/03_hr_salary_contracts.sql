use peoplecore;
-- =====================================================================
-- HR Service 더미 연봉계약 (SalaryContract × 100건 + Detail × 400건)
-- ---------------------------------------------------------------------
-- 02_hr_employees.sql 실행 후 그대로 이어서 실행.
--
-- [구성]
--   각 사원별 1건의 현재 적용 연봉계약 + 4개 detail (기본급/직책수당/식대/교통비)
--   apply_from = 입사일, apply_to = 퇴직일/계약만료/NULL(정규직)
--
-- [연봉 산정]
--   (직급별 기본급 + 직책별 직책수당 + 식대 200,000 + 교통비 200,000) × 12
--
--   직급 월 기본급:
--     사원 G1: 2,100,000  / 대리 G2: 3,100,000  / 과장 G3: 4,100,000
--     차장 G4: 5,100,000  / 부장 G5: 6,500,000  / 이사 G6: 9,500,000
--   직책 월 수당:
--     팀원   : 0          / 팀장   : 200,000
--     본부장 : 500,000    / 대표   : 1,000,000
--   식대 / 교통비 : 각 200,000 (비과세 한도 정확히 맞춤)
--
-- [백엔드 검증]
--   annual_total == fixed_monthly_sum × 12  (4항목 모두 isFixed=true)
--   ∴ 위 산정식대로 INSERT 하면 ANNUAL_SALARY_MISMATCH 미발생
--
-- [실행 방법]
--   $ mysql -u <user> -p peoplecore < 03_hr_salary_contracts.sql
-- =====================================================================

-- ▼ 회사 ▼
SET @company_name := 'peoplecore';
SET @cid := (SELECT company_id FROM company WHERE company_name = @company_name);

SELECT
  IFNULL(BIN_TO_UUID(@cid),
         CONCAT('❌ 회사를 찾을 수 없습니다: ', @company_name)) AS resolved_company;

-- ▼ 직급 ID lookup ▼
SET @g_emp := (SELECT grade_id FROM grade WHERE company_id=@cid AND grade_code='G1');
SET @g_dae := (SELECT grade_id FROM grade WHERE company_id=@cid AND grade_code='G2');
SET @g_gwa := (SELECT grade_id FROM grade WHERE company_id=@cid AND grade_code='G3');
SET @g_cha := (SELECT grade_id FROM grade WHERE company_id=@cid AND grade_code='G4');
SET @g_bu  := (SELECT grade_id FROM grade WHERE company_id=@cid AND grade_code='G5');
SET @g_isa := (SELECT grade_id FROM grade WHERE company_id=@cid AND grade_code='G6');

-- ▼ 직책 ID lookup ▼
SET @t_mem  := (SELECT title_id FROM title WHERE company_id=@cid AND title_code='T-MEMBER');
SET @t_lead := (SELECT title_id FROM title WHERE company_id=@cid AND title_code='T-LEAD');
SET @t_head := (SELECT title_id FROM title WHERE company_id=@cid AND title_code='T-HEAD');
SET @t_ceo  := (SELECT title_id FROM title WHERE company_id=@cid AND title_code='T-CEO');

-- ▼ 급여항목 ID lookup (회사 생성 시 자동 시드된 4개 고정 항목) ▼
SET @pi_basic := (SELECT pay_item_id FROM pay_items WHERE company_id=@cid AND pay_item_name='기본급');
SET @pi_pos   := (SELECT pay_item_id FROM pay_items WHERE company_id=@cid AND pay_item_name='직책수당');
SET @pi_meal  := (SELECT pay_item_id FROM pay_items WHERE company_id=@cid AND pay_item_name='식대');
SET @pi_trans := (SELECT pay_item_id FROM pay_items WHERE company_id=@cid AND pay_item_name='교통비');

-- ▼ 작성자 (HR_SUPER_ADMIN, EMP-2025-001 김민준) ▼
SET @e_creator := (SELECT emp_id FROM employee WHERE company_id=@cid AND emp_num='EMP-2025-001');

-- ▼ lookup 검증 (NULL 있으면 자동 시드 누락) ▼
SELECT
  @g_emp AS g_emp, @g_dae AS g_dae, @g_gwa AS g_gwa, @g_cha AS g_cha, @g_bu AS g_bu, @g_isa AS g_isa,
  @t_mem AS t_mem, @t_lead AS t_lead, @t_head AS t_head, @t_ceo AS t_ceo,
  @pi_basic AS pi_basic, @pi_pos AS pi_pos, @pi_meal AS pi_meal, @pi_trans AS pi_trans,
  @e_creator AS e_creator;

-- ▼ 폼 스냅샷 (회사 전역 - 모든 계약 동일) ▼
-- FormFieldSetupService.buildSalaryContractDefaults() + 동적 payItem 항목과 동일 구조
SET @form_snapshot := JSON_ARRAY(
  -- 인적사항
  JSON_OBJECT('fieldKey','empSearch','label','사원 검색','section','인적사항',
              'fieldType','SEARCH','visible',TRUE,'required',TRUE,'sortOrder',1,
              'options',NULL,'autoFillFrom',NULL,'locked',FALSE),
  JSON_OBJECT('fieldKey','department','label','부서','section','인적사항',
              'fieldType','TEXT','visible',TRUE,'required',TRUE,'sortOrder',2,
              'options',NULL,'autoFillFrom','department','locked',FALSE),
  JSON_OBJECT('fieldKey','rank','label','직급','section','인적사항',
              'fieldType','TEXT','visible',TRUE,'required',TRUE,'sortOrder',3,
              'options',NULL,'autoFillFrom','rank','locked',FALSE),
  JSON_OBJECT('fieldKey','position','label','직책','section','인적사항',
              'fieldType','TEXT','visible',TRUE,'required',TRUE,'sortOrder',4,
              'options',NULL,'autoFillFrom','position','locked',FALSE),
  JSON_OBJECT('fieldKey','employType','label','근로형태','section','인적사항',
              'fieldType','TEXT','visible',TRUE,'required',TRUE,'sortOrder',5,
              'options',NULL,'autoFillFrom','employType','locked',FALSE),
  -- 계약기간
  JSON_OBJECT('fieldKey','contractStart','label','계약 시작일','section','계약기간',
              'fieldType','DATE','visible',TRUE,'required',TRUE,'sortOrder',1,
              'options',NULL,'autoFillFrom',NULL,'locked',FALSE),
  JSON_OBJECT('fieldKey','contractEnd','label','계약 종료일','section','계약기간',
              'fieldType','DATE','visible',TRUE,'required',FALSE,'sortOrder',2,
              'options',NULL,'autoFillFrom',NULL,'locked',FALSE),
  JSON_OBJECT('fieldKey','weeklyHours','label','주당 근로시간','section','계약기간',
              'fieldType','SELECT','visible',TRUE,'required',TRUE,'sortOrder',3,
              'options',JSON_ARRAY('40시간 (주 5일)','35시간','30시간','20시간 (시간제)','15시간 (단시간)'),
              'autoFillFrom',NULL,'locked',FALSE),
  -- 급여 (고정 annualSalary + 동적 payItem)
  JSON_OBJECT('fieldKey','annualSalary','label','연봉','section','급여',
              'fieldType','NUMBER','visible',TRUE,'required',FALSE,'sortOrder',0,
              'options',NULL,'autoFillFrom',NULL,'locked',FALSE),
  JSON_OBJECT('fieldKey',CONCAT('payItem_',@pi_basic),'label','기본급','section','급여',
              'fieldType','NUMBER','visible',TRUE,'required',FALSE,'sortOrder',1,
              'options',NULL,'autoFillFrom',NULL,'locked',FALSE,'isFixed',TRUE),
  JSON_OBJECT('fieldKey',CONCAT('payItem_',@pi_pos),'label','직책수당','section','급여',
              'fieldType','NUMBER','visible',TRUE,'required',FALSE,'sortOrder',2,
              'options',NULL,'autoFillFrom',NULL,'locked',FALSE,'isFixed',TRUE),
  JSON_OBJECT('fieldKey',CONCAT('payItem_',@pi_meal),'label','식대','section','급여',
              'fieldType','NUMBER','visible',TRUE,'required',FALSE,'sortOrder',3,
              'options',NULL,'autoFillFrom',NULL,'locked',FALSE,'isFixed',TRUE),
  JSON_OBJECT('fieldKey',CONCAT('payItem_',@pi_trans),'label','교통비','section','급여',
              'fieldType','NUMBER','visible',TRUE,'required',FALSE,'sortOrder',4,
              'options',NULL,'autoFillFrom',NULL,'locked',FALSE,'isFixed',TRUE),
  -- 기타사항
  JSON_OBJECT('fieldKey','memo','label','특약사항 / 메모','section','기타사항',
              'fieldType','TEXTAREA','visible',TRUE,'required',FALSE,'sortOrder',1,
              'options',NULL,'autoFillFrom',NULL,'locked',FALSE),
  JSON_OBJECT('fieldKey','attachment','label','서명 완료 계약서 첨부','section','기타사항',
              'fieldType','FILE','visible',TRUE,'required',FALSE,'sortOrder',2,
              'options',NULL,'autoFillFrom',NULL,'locked',FALSE)
);


-- =====================================================================
-- 1) salary_contract — 사원당 1건 (총 100건)
-- ---------------------------------------------------------------------
--   total_amount = (직급별 기본급 + 직책별 수당 + 식대 + 교통비) × 12
--   apply_from   = 입사일
--   apply_to     = 퇴직일(RESIGNED) / 계약만료일(CONTRACT) / NULL(FULL)
-- =====================================================================

INSERT INTO salary_contract
  (emp_id, company_id, create_by, total_amount,
   apply_from, apply_to, file_name, original_file_name, content_type, file_size,
   form_values, form_snapshot, form_version, created_at, delete_at)
SELECT
  e.emp_id,
  e.company_id,
  @e_creator,
  (
    CASE e.grade_id
      WHEN @g_emp THEN 2100000
      WHEN @g_dae THEN 3100000
      WHEN @g_gwa THEN 4100000
      WHEN @g_cha THEN 5100000
      WHEN @g_bu  THEN 6500000
      WHEN @g_isa THEN 9500000
      ELSE 0
    END
    + CASE e.title_id
      WHEN @t_mem  THEN 0
      WHEN @t_lead THEN 200000
      WHEN @t_head THEN 500000
      WHEN @t_ceo  THEN 1000000
      ELSE 0
    END
    + 400000              -- 식대 200,000 + 교통비 200,000
  ) * 12                  AS total_amount,
  e.emp_hire_date         AS apply_from,
  CASE
    WHEN e.emp_status = 'RESIGNED' THEN e.emp_resign
    WHEN e.emp_type   = 'CONTRACT' THEN e.contract_end_date
    ELSE NULL
  END                     AS apply_to,
  NULL, NULL, NULL, NULL, -- 첨부파일 없음
  NULL, NULL, NULL,       -- form_values / form_snapshot / form_version (아래 UPDATE에서 채움)
  NOW(),                  -- created_at
  NULL                    -- delete_at (soft delete 안 됨)
FROM employee e
WHERE e.company_id = @cid;


-- =====================================================================
-- 2) salary_contract_detail — 계약당 4건 (총 400건)
-- ---------------------------------------------------------------------
--   기본급 / 직책수당 / 식대 / 교통비 4개 항목 (모두 isFixed=true)
--   백엔드 검증식: annual_total == fixed_monthly_sum × 12 → 정합성 통과
-- =====================================================================

-- 2-1. 기본급
INSERT INTO salary_contract_detail (contract_id, pay_item_id, amount)
SELECT sc.contract_id, @pi_basic,
       CASE e.grade_id
         WHEN @g_emp THEN 2100000
         WHEN @g_dae THEN 3100000
         WHEN @g_gwa THEN 4100000
         WHEN @g_cha THEN 5100000
         WHEN @g_bu  THEN 6500000
         WHEN @g_isa THEN 9500000
         ELSE 0
       END
  FROM salary_contract sc
  JOIN employee e ON e.emp_id = sc.emp_id
 WHERE sc.company_id = @cid;

-- 2-2. 직책수당
INSERT INTO salary_contract_detail (contract_id, pay_item_id, amount)
SELECT sc.contract_id, @pi_pos,
       CASE e.title_id
         WHEN @t_mem  THEN 0
         WHEN @t_lead THEN 200000
         WHEN @t_head THEN 500000
         WHEN @t_ceo  THEN 1000000
         ELSE 0
       END
  FROM salary_contract sc
  JOIN employee e ON e.emp_id = sc.emp_id
 WHERE sc.company_id = @cid;

-- 2-3. 식대 (전원 200,000)
INSERT INTO salary_contract_detail (contract_id, pay_item_id, amount)
SELECT sc.contract_id, @pi_meal, 200000
  FROM salary_contract sc
 WHERE sc.company_id = @cid;

-- 2-4. 교통비 (전원 200,000)
INSERT INTO salary_contract_detail (contract_id, pay_item_id, amount)
SELECT sc.contract_id, @pi_trans, 200000
  FROM salary_contract sc
 WHERE sc.company_id = @cid;


-- =====================================================================
-- 3) form_snapshot / form_version — 모든 계약 동일
-- =====================================================================
UPDATE salary_contract sc
SET sc.form_snapshot = @form_snapshot,
    sc.form_version  = UNIX_TIMESTAMP(NOW(3)) * 1000
WHERE sc.company_id = @cid;


-- =====================================================================
-- 4) form_values — 사원별 인적사항/계약기간/연봉/메모
-- ---------------------------------------------------------------------
--   payItem_<id> 값은 toDetailRes 가 salary_contract_detail 에서 자동 합성
--   → 일반 필드만 채워두면 상세보기에서 모든 항목이 보임
-- =====================================================================
UPDATE salary_contract sc
JOIN employee   e ON e.emp_id   = sc.emp_id
JOIN department d ON d.dept_id  = e.dept_id
JOIN grade      g ON g.grade_id = e.grade_id
LEFT JOIN title t ON t.title_id = e.title_id
SET sc.form_values = JSON_OBJECT(
      'empSearch',     e.emp_name,
      'department',    d.dept_name,
      'rank',          g.grade_name,
      'position',      IFNULL(t.title_name, ''),
      'employType',    CASE e.emp_type
                         WHEN 'FULL'     THEN '정규직'
                         WHEN 'CONTRACT' THEN '계약직'
                         ELSE e.emp_type
                       END,
      'contractStart', DATE_FORMAT(sc.apply_from, '%Y-%m-%d'),
      'contractEnd',   COALESCE(DATE_FORMAT(sc.apply_to, '%Y-%m-%d'), ''),
      'weeklyHours',   '40시간 (주 5일)',
      'annualSalary',  CAST(sc.total_amount AS CHAR),
      'memo',          ''
    )
WHERE sc.company_id = @cid;


-- =====================================================================
-- [검증 쿼리]
-- =====================================================================
-- 계약 100건 / detail 400건 카운트
-- SELECT 'salary_contract'        AS tbl, COUNT(*) AS cnt
--   FROM salary_contract        WHERE company_id = @cid
-- UNION ALL
-- SELECT 'salary_contract_detail',        COUNT(*)
--   FROM salary_contract_detail scd
--   JOIN salary_contract sc ON sc.contract_id = scd.contract_id
--  WHERE sc.company_id = @cid;
-- 예상: 100 / 400

-- 직급별 평균 연봉 분포
-- SELECT g.grade_name,
--        COUNT(*)               AS cnt,
--        MIN(sc.total_amount)   AS min_annual,
--        MAX(sc.total_amount)   AS max_annual,
--        AVG(sc.total_amount)   AS avg_annual
--   FROM salary_contract sc
--   JOIN employee e ON e.emp_id = sc.emp_id
--   JOIN grade    g ON g.grade_id = e.grade_id
--  WHERE sc.company_id = @cid
--  GROUP BY g.grade_id, g.grade_name
--  ORDER BY g.grade_order;
-- 예상 (직책수당 0 기준):
--   사원 G1: 30,000,000  / 대리 G2: 42,000,000
--   과장 G3: 54,000,000  / 차장 G4: 66,000,000
--   부장 G5: 82,800,000+ / 이사 G6: 118,800,000~130,800,000

-- 활성 계약(현재 적용중)만 카운트 — 백엔드 buildActiveContractMap 동일 조건
-- SELECT COUNT(*) AS active_contracts
--   FROM salary_contract
--  WHERE company_id = @cid
--    AND delete_at IS NULL
--    AND (apply_from IS NULL OR apply_from <= CURDATE())
--    AND (apply_to   IS NULL OR apply_to   >= CURDATE());
-- 예상: 95~98 (RESIGNED 2명, 입사일 미래/계약만료 케이스 따라 변동)
