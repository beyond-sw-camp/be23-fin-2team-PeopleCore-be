        use peoplecore;
        -- =====================================================================
        -- HR Service 더미 사원 데이터 (Employee × 100명) — lookup 변수 방식
        -- ---------------------------------------------------------------------
        -- 선행 조건:
        --   1) 회사 'peoplecore' 생성 완료 (자동 마스터 데이터 시드 포함)
        --   2) 01_hr_master_data.sql 실행 완료 (Department/Grade/Title/WorkGroup 추가)
        --
        --   본 스크립트는 명시 ID 에 의존하지 않고, dept_code/grade_code/title_code/
        --   job_type_name 으로 ID 를 lookup 해 변수에 담은 후 사용함.
        --   → 자동 시드 데이터(미배정 dept, 표준 산재 19종 등)와 ID 충돌 없음.
        --
        -- [실행 방법]
        --   $ mysql -u <user> -p peoplecore < 02_hr_employees.sql
        --
        -- [더미 비밀번호]
        --   전원 동일 BCrypt 해시. 평문은 'password'.
        --   해시: $2a$10$EblZqNptyYvcLm/VwDCVAuBjzZOI7khzdyGPBr08PpIi0na624b8.
        --
        -- [분포]
        --   부서   : 임원실 4 / 인사 10 / 재무 8 / 개발 35 / 인프라 12 / 영업 18 / 마케팅 13
        --   직급   : 사원 35 / 대리 25 / 과장 20 / 차장 12 / 부장 4 / 이사 4
        --   직책   : 대표 1 / 본부장 3 / 팀장 6 / 팀원 90
        --   재직   : ACTIVE 95 / ON_LEAVE 3 (id 17,50,83) / RESIGNED 2 (id 35,76)
        --   고용   : FULL 90 / CONTRACT 10 (contract_end='2027-12-31')
        --   권한   : HR_SUPER_ADMIN 1(대표) / HR_ADMIN 2(인사 5,6) / EMPLOYEE 97
        --   업종FK : 임원/인사/재무 → 금융/보험업 / 개발/인프라 → IT/소프트웨어
        --            영업 → 도소매업          / 마케팅 → 기타 서비스업
        --   근무그룹: 전원 NULL. 회사 기본 9-18 그룹 매핑은 INSERT 후 일괄 UPDATE.
        --
        -- [기본 워크그룹 일괄 매핑 — INSERT 후 별도 실행]
        --   UPDATE employee
        --      SET work_group_id = (SELECT work_group_id FROM work_group
        --                            WHERE company_id = (SELECT company_id FROM company WHERE company_name='peoplecore')
        --                              AND group_code = '<자동 9-18 그룹의 group_code>'),
        --          work_group_assigned_at = NOW()
        --    WHERE company_id = (SELECT company_id FROM company WHERE company_name='peoplecore')
        --      AND work_group_id IS NULL;
        -- =====================================================================

        -- ▼ 회사 + 비밀번호 ▼
        SET @company_name := 'peoplecore';
        SET @cid := (SELECT company_id FROM company WHERE company_name = @company_name);
        SET @pwd := '$2a$10$EblZqNptyYvcLm/VwDCVAuBjzZOI7khzdyGPBr08PpIi0na624b8.';

        SELECT
          IFNULL(BIN_TO_UUID(@cid),
                 CONCAT('❌ 회사를 찾을 수 없습니다: ', @company_name)) AS resolved_company;

        -- ▼ 부서 ID lookup (01 에서 추가한 dept_code 기준) ▼
        SET @d_exec  := (SELECT dept_id FROM department WHERE company_id=@cid AND dept_code='EXEC');
        SET @d_hr    := (SELECT dept_id FROM department WHERE company_id=@cid AND dept_code='HR');
        SET @d_fin   := (SELECT dept_id FROM department WHERE company_id=@cid AND dept_code='FIN');
        SET @d_dev   := (SELECT dept_id FROM department WHERE company_id=@cid AND dept_code='DEV');
        SET @d_inf   := (SELECT dept_id FROM department WHERE company_id=@cid AND dept_code='INF');
        SET @d_sales := (SELECT dept_id FROM department WHERE company_id=@cid AND dept_code='SALES');
        SET @d_mkt   := (SELECT dept_id FROM department WHERE company_id=@cid AND dept_code='MKT');
        SET @d_design:= (SELECT dept_id FROM department WHERE company_id=@cid AND dept_code='DESIGN');

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

        -- ▼ 산재보험 ID lookup (자동 시드 19개 중 4개) ▼
        SET @j_fin  := (SELECT job_types_id FROM insurance_job_types WHERE company_id=@cid AND job_type_name='금융/보험업');
        SET @j_it   := (SELECT job_types_id FROM insurance_job_types WHERE company_id=@cid AND job_type_name='IT/소프트웨어');
        SET @j_dist := (SELECT job_types_id FROM insurance_job_types WHERE company_id=@cid AND job_type_name='도소매업');
        SET @j_etc  := (SELECT job_types_id FROM insurance_job_types WHERE company_id=@cid AND job_type_name='기타 서비스업');

        -- ▼ lookup 결과 검증 (NULL 있으면 위 마스터 데이터 누락) ▼
        SELECT
          @d_exec AS d_exec, @d_hr AS d_hr, @d_fin AS d_fin, @d_dev AS d_dev, @d_inf AS d_inf, @d_sales AS d_sales, @d_mkt AS d_mkt,
          @g_emp AS g_emp, @g_dae AS g_dae, @g_gwa AS g_gwa, @g_cha AS g_cha, @g_bu AS g_bu, @g_isa AS g_isa,
          @t_mem AS t_mem, @t_lead AS t_lead, @t_head AS t_head, @t_ceo AS t_ceo,
          @j_fin AS j_fin, @j_it AS j_it, @j_dist AS j_dist, @j_etc AS j_etc;


        -- =====================================================================
        -- Employee × 100명 (단일 INSERT, multi-row VALUES, lookup 변수 사용)
        -- ---------------------------------------------------------------------
        -- 컬럼 순서:
        --   company_id, dept_id, grade_id, title_id, insurance_job_types,
        --   emp_name, emp_email, emp_phone, emp_num,
        --   emp_hire_date, emp_type, emp_status, emp_password, emp_role,
        --   emp_birth_date, emp_gender,
        --   emp_resign, contract_end_date,
        --   dependents_count, tax_rate_option, retirement_type, must_change_password
        -- =====================================================================

        INSERT INTO employee (
          company_id, dept_id, grade_id, title_id, insurance_job_types,
          emp_name, emp_email, emp_phone, emp_num,
          emp_hire_date, emp_type, emp_status, emp_password, emp_role,
          emp_birth_date, emp_gender,
          emp_resign, contract_end_date,
          dependents_count, tax_rate_option, retirement_type, must_change_password
        ) VALUES
        -- ───── 임원실 (EXEC) ─────
        (@cid, @d_exec,  @g_isa, @t_ceo,  @j_fin, '김민준', 'emp001@peoplecore.kr', '010-2001-4001', 'EMP-2025-001', '2010-03-02', 'FULL', 'ACTIVE', @pwd, 'HR_SUPER_ADMIN', '1965-03-15', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_exec,  @g_isa, @t_head, @j_fin, '이서연', 'emp002@peoplecore.kr', '010-2002-4002', 'EMP-2025-002', '2012-06-18', 'FULL', 'ACTIVE', @pwd, 'EMPLOYEE',       '1968-08-22', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_exec,  @g_isa, @t_head, @j_fin, '박지호', 'emp003@peoplecore.kr', '010-2003-4003', 'EMP-2025-003', '2013-09-04', 'FULL', 'ACTIVE', @pwd, 'EMPLOYEE',       '1970-11-05', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_exec,  @g_isa, @t_head, @j_fin, '정수아', 'emp004@peoplecore.kr', '010-2004-4004', 'EMP-2025-004', '2014-11-22', 'FULL', 'ACTIVE', @pwd, 'EMPLOYEE',       '1972-06-18', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        -- ───── 인사팀 (HR) ─────
        (@cid, @d_hr,    @g_bu,  @t_lead, @j_fin, '최도윤', 'emp005@peoplecore.kr', '010-2005-4005', 'EMP-2025-005', '2014-04-15', 'FULL', 'ACTIVE', @pwd, 'HR_ADMIN', '1975-09-12', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_hr,    @g_cha, @t_mem,  @j_fin, '강시우', 'emp006@peoplecore.kr', '010-2006-4006', 'EMP-2025-006', '2018-02-26', 'FULL', 'ACTIVE', @pwd, 'HR_ADMIN', '1981-04-28', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_hr,    @g_gwa, @t_mem,  @j_fin, '윤하준', 'emp007@peoplecore.kr', '010-2007-4007', 'EMP-2025-007', '2020-04-15', 'FULL', 'ACTIVE', @pwd, 'EMPLOYEE', '1985-07-15', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_hr,    @g_gwa, @t_mem,  @j_fin, '장지유', 'emp008@peoplecore.kr', '010-2008-4008', 'EMP-2025-008', '2021-07-22', 'FULL', 'ACTIVE', @pwd, 'EMPLOYEE', '1986-12-03', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_hr,    @g_dae, @t_mem,  @j_fin, '임예준', 'emp009@peoplecore.kr', '010-2009-4009', 'EMP-2025-009', '2022-05-13', 'CONTRACT', 'ACTIVE', @pwd, 'EMPLOYEE', '1989-05-20', 'MALE',   NULL, '2026-05-15', 1, 100, 'DC', FALSE),
        (@cid, @d_hr,    @g_dae, @t_mem,  @j_fin, '한서윤', 'emp010@peoplecore.kr', '010-2010-4010', 'EMP-2025-010', '2023-02-08', 'FULL', 'ACTIVE', @pwd, 'EMPLOYEE', '1990-08-14', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_hr,    @g_emp, @t_mem,  @j_fin, '오현우', 'emp011@peoplecore.kr', '010-2011-4011', 'EMP-2025-011', '2023-05-26', 'FULL', 'ACTIVE', @pwd, 'EMPLOYEE', '1996-02-09', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_hr,    @g_emp, @t_mem,  @j_fin, '신유진', 'emp012@peoplecore.kr', '010-2012-4012', 'EMP-2025-012', '2024-01-08', 'FULL', 'ACTIVE', @pwd, 'EMPLOYEE', '1998-06-22', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_hr,    @g_emp, @t_mem,  @j_fin, '권지훈', 'emp013@peoplecore.kr', '010-2013-4013', 'EMP-2025-013', '2024-08-19', 'FULL', 'ACTIVE', @pwd, 'EMPLOYEE', '1999-10-30', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_hr,    @g_emp, @t_mem,  @j_fin, '조서현', 'emp014@peoplecore.kr', '010-2014-4014', 'EMP-2025-014', '2025-02-04', 'FULL', 'ACTIVE', @pwd, 'EMPLOYEE', '2000-03-17', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        -- ───── 재무팀 (FIN) ─────
        (@cid, @d_fin,   @g_bu,  @t_lead, @j_fin, '백건우', 'emp015@peoplecore.kr', '010-2015-4015', 'EMP-2025-015', '2015-08-20', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1976-11-08', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_fin,   @g_cha, @t_mem,  @j_fin, '송하은', 'emp016@peoplecore.kr', '010-2016-4016', 'EMP-2025-016', '2019-05-13', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1981-07-25', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_fin,   @g_gwa, @t_mem,  @j_fin, '노지원', 'emp017@peoplecore.kr', '010-2017-4017', 'EMP-2025-017', '2020-09-08', 'FULL', 'ON_LEAVE', @pwd, 'EMPLOYEE', '1986-04-13', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_fin,   @g_dae, @t_mem,  @j_fin, '홍시현', 'emp018@peoplecore.kr', '010-2018-4018', 'EMP-2025-018', '2022-09-26', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1990-09-30', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_fin,   @g_dae, @t_mem,  @j_fin, '안주원', 'emp019@peoplecore.kr', '010-2019-4019', 'EMP-2025-019', '2023-04-12', 'CONTRACT', 'ACTIVE', @pwd, 'EMPLOYEE', '1991-02-18', 'MALE',   NULL, '2026-05-20', 1, 100, 'DC', FALSE),
        (@cid, @d_fin,   @g_emp, @t_mem,  @j_fin, '류다은', 'emp020@peoplecore.kr', '010-2020-4020', 'EMP-2025-020', '2023-07-13', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1996-12-07', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_fin,   @g_emp, @t_mem,  @j_fin, '배현준', 'emp021@peoplecore.kr', '010-2021-4021', 'EMP-2025-021', '2024-04-22', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1998-05-25', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_fin,   @g_emp, @t_mem,  @j_fin, '서지안', 'emp022@peoplecore.kr', '010-2022-4022', 'EMP-2025-022', '2025-01-15', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '2000-08-11', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        -- ───── 개발팀 (DEV) ─────
        (@cid, @d_dev,   @g_bu,  @t_lead, @j_it,  '남도현', 'emp023@peoplecore.kr', '010-2023-4023', 'EMP-2025-023', '2014-11-08', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1977-04-08', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_dev,   @g_cha, @t_mem,  @j_it,  '문하윤', 'emp024@peoplecore.kr', '010-2024-4024', 'EMP-2025-024', '2018-07-09', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1980-09-18', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_dev,   @g_cha, @t_mem,  @j_it,  '양준서', 'emp025@peoplecore.kr', '010-2025-4025', 'EMP-2025-025', '2019-03-25', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1982-12-05', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_dev,   @g_cha, @t_mem,  @j_it,  '진서영', 'emp026@peoplecore.kr', '010-2026-4026', 'EMP-2025-026', '2020-01-15', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1983-06-22', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_dev,   @g_gwa, @t_mem,  @j_it,  '차예린', 'emp027@peoplecore.kr', '010-2027-4027', 'EMP-2025-027', '2019-08-26', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1984-02-14', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_dev,   @g_gwa, @t_mem,  @j_it,  '구민서', 'emp028@peoplecore.kr', '010-2028-4028', 'EMP-2025-028', '2020-02-14', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1985-05-30', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_dev,   @g_gwa, @t_mem,  @j_it,  '표하윤', 'emp029@peoplecore.kr', '010-2029-4029', 'EMP-2025-029', '2020-06-30', 'CONTRACT', 'ACTIVE', @pwd, 'EMPLOYEE', '1985-11-08', 'FEMALE', NULL, '2026-05-25', 1, 100, 'DC', FALSE),
        (@cid, @d_dev,   @g_gwa, @t_mem,  @j_it,  '은지호', 'emp030@peoplecore.kr', '010-2030-4030', 'EMP-2025-030', '2020-11-19', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1986-08-19', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_dev,   @g_gwa, @t_mem,  @j_it,  '원유나', 'emp031@peoplecore.kr', '010-2031-4031', 'EMP-2025-031', '2021-04-08', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1987-03-26', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_dev,   @g_gwa, @t_mem,  @j_it,  '추서준', 'emp032@peoplecore.kr', '010-2032-4032', 'EMP-2025-032', '2021-09-15', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1987-10-12', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_dev,   @g_gwa, @t_mem,  @j_it,  '변하준', 'emp033@peoplecore.kr', '010-2033-4033', 'EMP-2025-033', '2022-01-25', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1988-04-05', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_dev,   @g_dae, @t_mem,  @j_it,  '도예원', 'emp034@peoplecore.kr', '010-2034-4034', 'EMP-2025-034', '2021-11-15', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1989-01-22', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_dev,   @g_dae, @t_mem,  @j_it,  '명소율', 'emp035@peoplecore.kr', '010-2035-4035', 'EMP-2025-035', '2022-03-08', 'FULL', 'RESIGNED', @pwd, 'EMPLOYEE', '1989-07-15', 'FEMALE', '2024-12-31', NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_dev,   @g_dae, @t_mem,  @j_it,  '형지유', 'emp036@peoplecore.kr', '010-2036-4036', 'EMP-2025-036', '2022-06-22', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1990-04-08', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_dev,   @g_dae, @t_mem,  @j_it,  '사하린', 'emp037@peoplecore.kr', '010-2037-4037', 'EMP-2025-037', '2022-09-30', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1990-11-19', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_dev,   @g_dae, @t_mem,  @j_it,  '두민호', 'emp038@peoplecore.kr', '010-2038-4038', 'EMP-2025-038', '2023-01-18', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1991-02-26', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_dev,   @g_dae, @t_mem,  @j_it,  '황건우', 'emp039@peoplecore.kr', '010-2039-4039', 'EMP-2025-039', '2023-05-04', 'CONTRACT', 'ACTIVE', @pwd, 'EMPLOYEE', '1991-09-13', 'MALE',   NULL, '2026-05-30', 1, 100, 'DC', FALSE),
        (@cid, @d_dev,   @g_dae, @t_mem,  @j_it,  '곽지수', 'emp040@peoplecore.kr', '010-2040-4040', 'EMP-2025-040', '2023-08-25', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1992-05-02', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_dev,   @g_dae, @t_mem,  @j_it,  '탁윤서', 'emp041@peoplecore.kr', '010-2041-4041', 'EMP-2025-041', '2023-12-11', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1992-12-14', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_dev,   @g_dae, @t_mem,  @j_it,  '위주아', 'emp042@peoplecore.kr', '010-2042-4042', 'EMP-2025-042', '2024-03-19', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1993-06-25', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_dev,   @g_dae, @t_mem,  @j_it,  '어재현', 'emp043@peoplecore.kr', '010-2043-4043', 'EMP-2025-043', '2024-07-02', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1993-10-30', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_dev,   @g_emp, @t_mem,  @j_it,  '옥도훈', 'emp044@peoplecore.kr', '010-2044-4044', 'EMP-2025-044', '2023-02-15', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1995-03-18', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_dev,   @g_emp, @t_mem,  @j_it,  '마지원', 'emp045@peoplecore.kr', '010-2045-4045', 'EMP-2025-045', '2023-05-30', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1996-07-22', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_dev,   @g_emp, @t_mem,  @j_it,  '부태민', 'emp046@peoplecore.kr', '010-2046-4046', 'EMP-2025-046', '2023-08-12', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1996-12-04', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_dev,   @g_emp, @t_mem,  @j_it,  '호윤재', 'emp047@peoplecore.kr', '010-2047-4047', 'EMP-2025-047', '2023-11-25', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1997-05-29', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_dev,   @g_emp, @t_mem,  @j_it,  '양수빈', 'emp048@peoplecore.kr', '010-2048-4048', 'EMP-2025-048', '2024-02-08', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1997-09-15', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_dev,   @g_emp, @t_mem,  @j_it,  '갈태양', 'emp049@peoplecore.kr', '010-2049-4049', 'EMP-2025-049', '2024-04-22', 'CONTRACT', 'ACTIVE', @pwd, 'EMPLOYEE', '1998-02-26', 'MALE',   NULL, '2026-06-03', 1, 100, 'DC', FALSE),
        (@cid, @d_dev,   @g_emp, @t_mem,  @j_it,  '용예성', 'emp050@peoplecore.kr', '010-2050-4050', 'EMP-2025-050', '2024-07-15', 'FULL', 'ON_LEAVE', @pwd, 'EMPLOYEE', '1998-08-11', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_dev,   @g_emp, @t_mem,  @j_it,  '엄정민', 'emp051@peoplecore.kr', '010-2051-4051', 'EMP-2025-051', '2024-09-30', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1999-04-19', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_dev,   @g_emp, @t_mem,  @j_it,  '음하경', 'emp052@peoplecore.kr', '010-2052-4052', 'EMP-2025-052', '2024-12-18', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1999-10-08', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_dev,   @g_emp, @t_mem,  @j_it,  '화서윤', 'emp053@peoplecore.kr', '010-2053-4053', 'EMP-2025-053', '2025-01-09', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '2000-01-23', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_dev,   @g_emp, @t_mem,  @j_it,  '시예진', 'emp054@peoplecore.kr', '010-2054-4054', 'EMP-2025-054', '2025-02-25', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '2000-06-15', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_dev,   @g_emp, @t_mem,  @j_it,  '아도윤', 'emp055@peoplecore.kr', '010-2055-4055', 'EMP-2025-055', '2025-04-14', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '2001-03-12', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_dev,   @g_emp, @t_mem,  @j_it,  '매지현', 'emp056@peoplecore.kr', '010-2056-4056', 'EMP-2025-056', '2025-05-30', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '2001-11-28', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_dev,   @g_emp, @t_mem,  @j_it,  '하태경', 'emp057@peoplecore.kr', '010-2057-4057', 'EMP-2025-057', '2025-08-22', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '2002-07-04', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        -- ───── 인프라팀 (INF) ─────
        (@cid, @d_inf,   @g_cha, @t_lead, @j_it,  '빈주영', 'emp058@peoplecore.kr', '010-2058-4058', 'EMP-2025-058', '2017-09-11', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1980-08-15', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_inf,   @g_cha, @t_mem,  @j_it,  '함채원', 'emp059@peoplecore.kr', '010-2059-4059', 'EMP-2025-059', '2019-10-08', 'CONTRACT', 'ACTIVE', @pwd, 'EMPLOYEE', '1982-12-22', 'FEMALE', NULL, '2026-06-07', 1, 100, 'DC', FALSE),
        (@cid, @d_inf,   @g_gwa, @t_mem,  @j_it,  '봉승호', 'emp060@peoplecore.kr', '010-2060-4060', 'EMP-2025-060', '2020-08-13', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1985-04-08', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_inf,   @g_gwa, @t_mem,  @j_it,  '방연우', 'emp061@peoplecore.kr', '010-2061-4061', 'EMP-2025-061', '2021-05-24', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1986-09-19', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_inf,   @g_gwa, @t_mem,  @j_it,  '라하늘', 'emp062@peoplecore.kr', '010-2062-4062', 'EMP-2025-062', '2022-02-07', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1987-12-30', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_inf,   @g_dae, @t_mem,  @j_it,  '모은채', 'emp063@peoplecore.kr', '010-2063-4063', 'EMP-2025-063', '2022-12-08', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1989-02-15', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_inf,   @g_dae, @t_mem,  @j_it,  '단지애', 'emp064@peoplecore.kr', '010-2064-4064', 'EMP-2025-064', '2023-06-19', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1990-07-23', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_inf,   @g_dae, @t_mem,  @j_it,  '우민혁', 'emp065@peoplecore.kr', '010-2065-4065', 'EMP-2025-065', '2024-02-26', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1991-10-05', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_inf,   @g_emp, @t_mem,  @j_it,  '가서영', 'emp066@peoplecore.kr', '010-2066-4066', 'EMP-2025-066', '2023-09-26', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1996-05-12', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_inf,   @g_emp, @t_mem,  @j_it,  '비주현', 'emp067@peoplecore.kr', '010-2067-4067', 'EMP-2025-067', '2024-05-13', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1998-08-26', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_inf,   @g_emp, @t_mem,  @j_it,  '그하영', 'emp068@peoplecore.kr', '010-2068-4068', 'EMP-2025-068', '2024-11-22', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1999-11-14', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_inf,   @g_emp, @t_mem,  @j_it,  '차도엽', 'emp069@peoplecore.kr', '010-2069-4069', 'EMP-2025-069', '2025-03-08', 'CONTRACT', 'ACTIVE', @pwd, 'EMPLOYEE', '2000-03-08', 'MALE',   NULL, '2027-12-31', 1, 100, 'DC', FALSE),
        -- ───── 영업팀 (SALES) ─────
        (@cid, @d_sales, @g_bu,  @t_lead, @j_dist,'즈경수', 'emp070@peoplecore.kr', '010-2070-4070', 'EMP-2025-070', '2015-06-12', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1976-08-24', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_sales, @g_cha, @t_mem,  @j_dist,'선재민', 'emp071@peoplecore.kr', '010-2071-4071', 'EMP-2025-071', '2018-12-04', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1980-11-30', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_sales, @g_cha, @t_mem,  @j_dist,'라윤혁', 'emp072@peoplecore.kr', '010-2072-4072', 'EMP-2025-072', '2019-08-19', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1982-05-14', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_sales, @g_gwa, @t_mem,  @j_dist,'성다현', 'emp073@peoplecore.kr', '010-2073-4073', 'EMP-2025-073', '2019-12-02', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1984-09-22', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_sales, @g_gwa, @t_mem,  @j_dist,'도연수', 'emp074@peoplecore.kr', '010-2074-4074', 'EMP-2025-074', '2020-07-19', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1985-12-08', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_sales, @g_gwa, @t_mem,  @j_dist,'설지영', 'emp075@peoplecore.kr', '010-2075-4075', 'EMP-2025-075', '2021-03-08', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1986-04-15', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_sales, @g_gwa, @t_mem,  @j_dist,'류태현', 'emp076@peoplecore.kr', '010-2076-4076', 'EMP-2025-076', '2021-10-25', 'FULL', 'RESIGNED', @pwd, 'EMPLOYEE', '1987-07-29', 'MALE', '2025-03-15', NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_sales, @g_gwa, @t_mem,  @j_dist,'류수민', 'emp077@peoplecore.kr', '010-2077-4077', 'EMP-2025-077', '2022-05-14', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1988-02-11', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_sales, @g_dae, @t_mem,  @j_dist,'정채린', 'emp078@peoplecore.kr', '010-2078-4078', 'EMP-2025-078', '2022-08-30', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1989-06-25', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_sales, @g_dae, @t_mem,  @j_dist,'송예지', 'emp079@peoplecore.kr', '010-2079-4079', 'EMP-2025-079', '2023-04-15', 'CONTRACT', 'ACTIVE', @pwd, 'EMPLOYEE', '1990-08-18', 'FEMALE', NULL, '2027-12-31', 1, 100, 'DC', FALSE),
        (@cid, @d_sales, @g_dae, @t_mem,  @j_dist,'윤지민', 'emp080@peoplecore.kr', '010-2080-4080', 'EMP-2025-080', '2023-11-22', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1991-12-04', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_sales, @g_dae, @t_mem,  @j_dist,'이주환', 'emp081@peoplecore.kr', '010-2081-4081', 'EMP-2025-081', '2024-05-06', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1992-04-19', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_sales, @g_emp, @t_mem,  @j_dist,'김서아', 'emp082@peoplecore.kr', '010-2082-4082', 'EMP-2025-082', '2023-04-19', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1996-08-13', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_sales, @g_emp, @t_mem,  @j_dist,'박태우', 'emp083@peoplecore.kr', '010-2083-4083', 'EMP-2025-083', '2023-08-26', 'FULL', 'ON_LEAVE', @pwd, 'EMPLOYEE', '1997-11-25', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_sales, @g_emp, @t_mem,  @j_dist,'최예나', 'emp084@peoplecore.kr', '010-2084-4084', 'EMP-2025-084', '2024-01-15', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1998-03-08', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_sales, @g_emp, @t_mem,  @j_dist,'한지석', 'emp085@peoplecore.kr', '010-2085-4085', 'EMP-2025-085', '2024-06-22', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1999-07-16', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_sales, @g_emp, @t_mem,  @j_dist,'안유나', 'emp086@peoplecore.kr', '010-2086-4086', 'EMP-2025-086', '2024-11-08', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '2000-12-22', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_sales, @g_emp, @t_mem,  @j_dist,'강민서', 'emp087@peoplecore.kr', '010-2087-4087', 'EMP-2025-087', '2025-04-30', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '2002-05-04', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        -- ───── 마케팅팀 (MKT) ─────
        (@cid, @d_mkt,   @g_cha, @t_lead, @j_etc, '조지율', 'emp088@peoplecore.kr', '010-2088-4088', 'EMP-2025-088', '2018-04-22', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1981-09-18', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_mkt,   @g_cha, @t_mem,  @j_etc, '홍연재', 'emp089@peoplecore.kr', '010-2089-4089', 'EMP-2025-089', '2019-11-12', 'CONTRACT', 'ACTIVE', @pwd, 'EMPLOYEE', '1983-12-12', 'MALE',   NULL, '2027-12-31', 1, 100, 'DC', FALSE),
        (@cid, @d_mkt,   @g_cha, @t_mem,  @j_etc, '임도하', 'emp090@peoplecore.kr', '010-2090-4090', 'EMP-2025-090', '2020-08-04', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1984-08-25', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_mkt,   @g_gwa, @t_mem,  @j_etc, '신유나', 'emp091@peoplecore.kr', '010-2091-4091', 'EMP-2025-091', '2020-10-30', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1985-06-25', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_mkt,   @g_gwa, @t_mem,  @j_etc, '양현서', 'emp092@peoplecore.kr', '010-2092-4092', 'EMP-2025-092', '2022-03-19', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1987-02-08', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_mkt,   @g_dae, @t_mem,  @j_etc, '백승현', 'emp093@peoplecore.kr', '010-2093-4093', 'EMP-2025-093', '2022-10-18', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1989-10-30', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_mkt,   @g_dae, @t_mem,  @j_etc, '서지유', 'emp094@peoplecore.kr', '010-2094-4094', 'EMP-2025-094', '2023-03-25', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1990-05-22', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_mkt,   @g_dae, @t_mem,  @j_etc, '문도윤', 'emp095@peoplecore.kr', '010-2095-4095', 'EMP-2025-095', '2023-09-13', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1991-08-15', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_mkt,   @g_dae, @t_mem,  @j_etc, '노수민', 'emp096@peoplecore.kr', '010-2096-4096', 'EMP-2025-096', '2024-04-30', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1992-11-04', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_mkt,   @g_emp, @t_mem,  @j_etc, '권채영', 'emp097@peoplecore.kr', '010-2097-4097', 'EMP-2025-097', '2023-11-15', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1996-04-26', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_mkt,   @g_emp, @t_mem,  @j_etc, '정호준', 'emp098@peoplecore.kr', '010-2098-4098', 'EMP-2025-098', '2024-06-12', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1998-07-15', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_mkt,   @g_emp, @t_mem,  @j_etc, '오수빈', 'emp099@peoplecore.kr', '010-2099-4099', 'EMP-2025-099', '2024-12-08', 'CONTRACT', 'ACTIVE', @pwd, 'EMPLOYEE', '1999-12-08', 'FEMALE', NULL, '2027-12-31', 1, 100, 'DC', FALSE),
        (@cid, @d_mkt,   @g_emp, @t_mem,  @j_etc, '황민재', 'emp100@peoplecore.kr', '010-2100-4100', 'EMP-2025-100', '2025-05-15', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '2001-06-30', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE);


        -- =====================================================================
        -- [추가 시드] 사직원/퇴직 시나리오용 사원 × 20명 (EMP-2025-101 ~ 120)
        -- ---------------------------------------------------------------------
        -- 4가지 케이스 × 각 5명. 모두 "충분한 근속(1년 이상)" 으로 퇴직금/연차수당
        -- 산정 가능하도록 emp_hire_date 를 과거로 박음.
        --
        -- (a) 사직원 결재 상신만   : emp_status=ACTIVE,   resign.retire_status=ACTIVE,    doc_id=1001~1005
        -- (b) 결재 승인 + 퇴직처리X: emp_status=ACTIVE,   resign.retire_status=ACTIVE,    doc_id=2001~2005
        --                            (DB row 는 (a) 와 동일. 외부 결재시스템 doc 상태가 APPROVED 인지만 다름)
        -- (c) 퇴직처리 + 미래일    : emp_status=ACTIVE,   resign.retire_status=CONFIRMED, doc_id=3001~3005
        --                            ※ ResignService.processResign() 호출시 emp_status 는 변경 안 됨.
        --                              실제 RESIGNED 전환은 스케줄러(processScheduledResigns)가 resign_date <= TODAY 에 수행.
        -- (d) 완전 퇴직 처리 완료  : emp_status=RESIGNED, resign.retire_status=RESIGNED,  doc_id=4001~4005
        -- =====================================================================

        INSERT INTO employee (
          company_id, dept_id, grade_id, title_id, insurance_job_types,
          emp_name, emp_email, emp_phone, emp_num,
          emp_hire_date, emp_type, emp_status, emp_password, emp_role,
          emp_birth_date, emp_gender,
          emp_resign, contract_end_date,
          dependents_count, tax_rate_option, retirement_type, must_change_password
        ) VALUES
        -- ───── (a) 사직원 결재 상신만 (5명) — emp_status=ACTIVE ─────
        (@cid, @d_dev,   @g_gwa, @t_mem,  @j_it,  '편하준', 'emp101@peoplecore.kr', '010-2101-4101', 'EMP-2025-101', '2017-08-22', 'FULL', 'ACTIVE', @pwd, 'EMPLOYEE', '1985-04-12', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_sales, @g_dae, @t_mem,  @j_dist,'경수아', 'emp102@peoplecore.kr', '010-2102-4102', 'EMP-2025-102', '2020-11-14', 'FULL', 'ACTIVE', @pwd, 'EMPLOYEE', '1991-09-25', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_mkt,   @g_emp, @t_mem,  @j_etc, '나도진', 'emp103@peoplecore.kr', '010-2103-4103', 'EMP-2025-103', '2019-04-30', 'FULL', 'ACTIVE', @pwd, 'EMPLOYEE', '1996-06-08', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_fin,   @g_dae, @t_mem,  @j_fin, '진가람', 'emp104@peoplecore.kr', '010-2104-4104', 'EMP-2025-104', '2018-09-08', 'FULL', 'ACTIVE', @pwd, 'EMPLOYEE', '1989-12-19', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_hr,    @g_emp, @t_mem,  @j_fin, '맹지호', 'emp105@peoplecore.kr', '010-2105-4105', 'EMP-2025-105', '2021-07-25', 'FULL', 'ACTIVE', @pwd, 'EMPLOYEE', '1995-02-04', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        -- ───── (b) 결재 승인 + 퇴직처리 미클릭 (5명) — emp_status=ACTIVE ─────
        (@cid, @d_dev,   @g_gwa, @t_mem,  @j_it,  '구재희', 'emp106@peoplecore.kr', '010-2106-4106', 'EMP-2025-106', '2019-06-15', 'FULL', 'ACTIVE', @pwd, 'EMPLOYEE', '1986-10-30', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_inf,   @g_dae, @t_mem,  @j_it,  '국승원', 'emp107@peoplecore.kr', '010-2107-4107', 'EMP-2025-107', '2021-03-22', 'FULL', 'ACTIVE', @pwd, 'EMPLOYEE', '1990-05-18', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_sales, @g_dae, @t_mem,  @j_dist,'궁아라', 'emp108@peoplecore.kr', '010-2108-4108', 'EMP-2025-108', '2020-08-10', 'FULL', 'ACTIVE', @pwd, 'EMPLOYEE', '1991-11-25', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_mkt,   @g_gwa, @t_mem,  @j_etc, '극서윤', 'emp109@peoplecore.kr', '010-2109-4109', 'EMP-2025-109', '2018-12-05', 'FULL', 'ACTIVE', @pwd, 'EMPLOYEE', '1985-08-22', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_hr,    @g_dae, @t_mem,  @j_fin, '근지온', 'emp110@peoplecore.kr', '010-2110-4110', 'EMP-2025-110', '2022-05-18', 'FULL', 'ACTIVE', @pwd, 'EMPLOYEE', '1992-04-09', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        -- ───── (c) 퇴직처리 + 퇴직일 미래 (5명) — emp_status=ACTIVE, retire_status=CONFIRMED ─────
        (@cid, @d_dev,   @g_cha, @t_mem,  @j_it,  '근태진', 'emp111@peoplecore.kr', '010-2111-4111', 'EMP-2025-111', '2018-09-10', 'FULL', 'ACTIVE', @pwd, 'EMPLOYEE', '1982-07-14', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_sales, @g_gwa, @t_mem,  @j_dist,'금영빈', 'emp112@peoplecore.kr', '010-2112-4112', 'EMP-2025-112', '2020-02-14', 'FULL', 'ACTIVE', @pwd, 'EMPLOYEE', '1988-03-26', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_mkt,   @g_gwa, @t_mem,  @j_etc, '기소은', 'emp113@peoplecore.kr', '010-2113-4113', 'EMP-2025-113', '2019-11-25', 'FULL', 'ACTIVE', @pwd, 'EMPLOYEE', '1986-12-19', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_fin,   @g_gwa, @t_mem,  @j_fin, '길도훈', 'emp114@peoplecore.kr', '010-2114-4114', 'EMP-2025-114', '2017-04-08', 'FULL', 'ACTIVE', @pwd, 'EMPLOYEE', '1980-10-05', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_inf,   @g_dae, @t_mem,  @j_it,  '나윤재', 'emp115@peoplecore.kr', '010-2115-4115', 'EMP-2025-115', '2022-12-19', 'FULL', 'ACTIVE', @pwd, 'EMPLOYEE', '1993-06-15', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        -- ───── (d) 완전 퇴직 처리 완료 (5명) — emp_status=RESIGNED ─────
        (@cid, @d_dev,   @g_dae, @t_mem,  @j_it,  '남보경', 'emp116@peoplecore.kr', '010-2116-4116', 'EMP-2025-116', '2019-03-15', 'FULL', 'RESIGNED', @pwd, 'EMPLOYEE', '1989-08-22', 'FEMALE', '2026-02-15', NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_sales, @g_gwa, @t_mem,  @j_dist,'노시혁', 'emp117@peoplecore.kr', '010-2117-4117', 'EMP-2025-117', '2020-08-22', 'FULL', 'RESIGNED', @pwd, 'EMPLOYEE', '1991-04-30', 'MALE',   '2025-09-30', NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_mkt,   @g_dae, @t_mem,  @j_etc, '도해린', 'emp118@peoplecore.kr', '010-2118-4118', 'EMP-2025-118', '2018-11-05', 'FULL', 'RESIGNED', @pwd, 'EMPLOYEE', '1986-12-13', 'FEMALE', '2025-12-31', NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_hr,    @g_dae, @t_mem,  @j_fin, '류은성', 'emp119@peoplecore.kr', '010-2119-4119', 'EMP-2025-119', '2021-04-13', 'FULL', 'RESIGNED', @pwd, 'EMPLOYEE', '1990-09-08', 'MALE',   '2026-03-15', NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_fin,   @g_gwa, @t_mem,  @j_fin, '문가온', 'emp120@peoplecore.kr', '010-2120-4120', 'EMP-2025-120', '2017-06-20', 'FULL', 'RESIGNED', @pwd, 'EMPLOYEE', '1984-11-25', 'FEMALE', '2026-01-31', NULL, 1, 100, 'DC', FALSE);


        -- =====================================================================
        -- [추가 시드] 과거 퇴직자 (EMP-HIST-001 ~ 030) — 2015~2024-11 분포
        -- ---------------------------------------------------------------------
        -- 월별 입퇴사 추이 차트가 풍부하게 표시되도록 10년치 퇴직자를 다이나믹 분포로 주입.
        -- 모두 emp_status='RESIGNED', emp_resign 채움. 평가/목표/매핑은 ACTIVE 필터에 자동 제외됨.
        -- 일부 월은 0건, 일부 월은 1~2건 (HR 트렌드 곡선이 자연스럽게 보이도록).
        -- =====================================================================
        INSERT INTO employee (
          company_id, dept_id, grade_id, title_id, insurance_job_types,
          emp_name, emp_email, emp_phone, emp_num,
          emp_hire_date, emp_type, emp_status, emp_password, emp_role,
          emp_birth_date, emp_gender,
          emp_resign, contract_end_date,
          dependents_count, tax_rate_option, retirement_type, must_change_password
        ) VALUES
        -- 2015 (2명)
        (@cid, @d_dev,   @g_dae, @t_mem, @j_it,  '김혜원', 'hist001@peoplecore.kr', '010-2200-1001', 'EMP-HIST-001', '2010-04-12', 'FULL', 'RESIGNED', @pwd, 'EMPLOYEE', '1985-06-10', 'FEMALE', '2015-03-15', NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_sales, @g_gwa, @t_mem, @j_dist,'이태성', 'hist002@peoplecore.kr', '010-2200-1002', 'EMP-HIST-002', '2012-09-08', 'FULL', 'RESIGNED', @pwd, 'EMPLOYEE', '1983-02-22', 'MALE',   '2015-08-22', NULL, 1, 100, 'DC', FALSE),
        -- 2016 (2명)
        (@cid, @d_hr,    @g_emp, @t_mem, @j_fin, '박은지', 'hist003@peoplecore.kr', '010-2200-1003', 'EMP-HIST-003', '2011-06-22', 'FULL', 'RESIGNED', @pwd, 'EMPLOYEE', '1990-11-05', 'FEMALE', '2016-02-10', NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_inf,   @g_dae, @t_mem, @j_it,  '최승우', 'hist004@peoplecore.kr', '010-2200-1004', 'EMP-HIST-004', '2014-03-15', 'FULL', 'RESIGNED', @pwd, 'EMPLOYEE', '1988-08-15', 'MALE',   '2016-09-05', NULL, 1, 100, 'DC', FALSE),
        -- 2017 (1명)
        (@cid, @d_fin,   @g_gwa, @t_mem, @j_fin, '정나영', 'hist005@peoplecore.kr', '010-2200-1005', 'EMP-HIST-005', '2013-11-08', 'FULL', 'RESIGNED', @pwd, 'EMPLOYEE', '1986-05-18', 'FEMALE', '2017-06-18', NULL, 1, 100, 'DC', FALSE),
        -- 2018 (3명)
        (@cid, @d_mkt,   @g_emp, @t_mem, @j_etc, '강민혁', 'hist006@peoplecore.kr', '010-2200-1006', 'EMP-HIST-006', '2015-05-20', 'FULL', 'RESIGNED', @pwd, 'EMPLOYEE', '1992-09-12', 'MALE',   '2018-01-25', NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_dev,   @g_dae, @t_mem, @j_it,  '조유진', 'hist007@peoplecore.kr', '010-2200-1007', 'EMP-HIST-007', '2014-08-15', 'FULL', 'RESIGNED', @pwd, 'EMPLOYEE', '1989-04-30', 'FEMALE', '2018-07-12', NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_sales, @g_gwa, @t_mem, @j_dist,'윤재석', 'hist008@peoplecore.kr', '010-2200-1008', 'EMP-HIST-008', '2016-02-25', 'FULL', 'RESIGNED', @pwd, 'EMPLOYEE', '1987-12-20', 'MALE',   '2018-11-30', NULL, 1, 100, 'DC', FALSE),
        -- 2019 (3명)
        (@cid, @d_hr,    @g_dae, @t_mem, @j_fin, '장혜린', 'hist009@peoplecore.kr', '010-2200-1009', 'EMP-HIST-009', '2015-10-30', 'FULL', 'RESIGNED', @pwd, 'EMPLOYEE', '1991-07-08', 'FEMALE', '2019-04-08', NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_inf,   @g_emp, @t_mem, @j_it,  '임도훈', 'hist010@peoplecore.kr', '010-2200-1010', 'EMP-HIST-010', '2017-04-12', 'FULL', 'RESIGNED', @pwd, 'EMPLOYEE', '1993-03-15', 'MALE',   '2019-08-15', NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_fin,   @g_gwa, @t_mem, @j_fin, '한지영', 'hist011@peoplecore.kr', '010-2200-1011', 'EMP-HIST-011', '2016-09-15', 'FULL', 'RESIGNED', @pwd, 'EMPLOYEE', '1988-10-22', 'FEMALE', '2019-12-22', NULL, 1, 100, 'DC', FALSE),
        -- 2020 (4명)
        (@cid, @d_dev,   @g_emp, @t_mem, @j_it,  '오현석', 'hist012@peoplecore.kr', '010-2200-1012', 'EMP-HIST-012', '2017-08-22', 'FULL', 'RESIGNED', @pwd, 'EMPLOYEE', '1994-01-15', 'MALE',   '2020-02-05', NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_mkt,   @g_dae, @t_mem, @j_etc, '신예솔', 'hist013@peoplecore.kr', '010-2200-1013', 'EMP-HIST-013', '2018-12-10', 'FULL', 'RESIGNED', @pwd, 'EMPLOYEE', '1992-08-05', 'FEMALE', '2020-05-18', NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_sales, @g_gwa, @t_mem, @j_dist,'권태우', 'hist014@peoplecore.kr', '010-2200-1014', 'EMP-HIST-014', '2017-04-08', 'FULL', 'RESIGNED', @pwd, 'EMPLOYEE', '1990-06-25', 'MALE',   '2020-08-25', NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_hr,    @g_emp, @t_mem, @j_fin, '황소연', 'hist015@peoplecore.kr', '010-2200-1015', 'EMP-HIST-015', '2019-02-15', 'FULL', 'RESIGNED', @pwd, 'EMPLOYEE', '1995-12-10', 'FEMALE', '2020-11-12', NULL, 1, 100, 'DC', FALSE),
        -- 2021 (3명)
        (@cid, @d_inf,   @g_dae, @t_mem, @j_it,  '안준영', 'hist016@peoplecore.kr', '010-2200-1016', 'EMP-HIST-016', '2018-07-22', 'FULL', 'RESIGNED', @pwd, 'EMPLOYEE', '1991-04-18', 'MALE',   '2021-03-09', NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_dev,   @g_emp, @t_mem, @j_it,  '송하린', 'hist017@peoplecore.kr', '010-2200-1017', 'EMP-HIST-017', '2019-09-30', 'FULL', 'RESIGNED', @pwd, 'EMPLOYEE', '1996-02-08', 'FEMALE', '2021-07-20', NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_fin,   @g_dae, @t_mem, @j_fin, '류지환', 'hist018@peoplecore.kr', '010-2200-1018', 'EMP-HIST-018', '2017-12-05', 'CONTRACT', 'RESIGNED', @pwd, 'EMPLOYEE', '1989-11-30', 'MALE', '2021-10-15', '2021-10-15', 1, 100, 'DC', FALSE),
        -- 2022 (4명)
        (@cid, @d_mkt,   @g_gwa, @t_mem, @j_etc, '배미경', 'hist019@peoplecore.kr', '010-2200-1019', 'EMP-HIST-019', '2019-04-18', 'FULL', 'RESIGNED', @pwd, 'EMPLOYEE', '1990-08-22', 'FEMALE', '2022-01-28', NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_sales, @g_emp, @t_mem, @j_dist,'서지호', 'hist020@peoplecore.kr', '010-2200-1020', 'EMP-HIST-020', '2020-08-10', 'FULL', 'RESIGNED', @pwd, 'EMPLOYEE', '1995-05-15', 'MALE',   '2022-05-10', NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_dev,   @g_emp, @t_mem, @j_it,  '문수아', 'hist021@peoplecore.kr', '010-2200-1021', 'EMP-HIST-021', '2021-01-25', 'FULL', 'RESIGNED', @pwd, 'EMPLOYEE', '1997-09-08', 'FEMALE', '2022-08-22', NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_hr,    @g_gwa, @t_mem, @j_fin, '진동현', 'hist022@peoplecore.kr', '010-2200-1022', 'EMP-HIST-022', '2019-11-15', 'FULL', 'RESIGNED', @pwd, 'EMPLOYEE', '1988-06-12', 'MALE',   '2022-11-30', NULL, 1, 100, 'DC', FALSE),
        -- 2023 (5명)
        (@cid, @d_inf,   @g_dae, @t_mem, @j_it,  '차예원', 'hist023@peoplecore.kr', '010-2200-1023', 'EMP-HIST-023', '2020-05-08', 'FULL', 'RESIGNED', @pwd, 'EMPLOYEE', '1991-12-25', 'FEMALE', '2023-02-14', NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_fin,   @g_emp, @t_mem, @j_fin, '구하영', 'hist024@peoplecore.kr', '010-2200-1024', 'EMP-HIST-024', '2021-03-20', 'FULL', 'RESIGNED', @pwd, 'EMPLOYEE', '1996-07-04', 'FEMALE', '2023-04-25', NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_mkt,   @g_emp, @t_mem, @j_etc, '표민호', 'hist025@peoplecore.kr', '010-2200-1025', 'EMP-HIST-025', '2020-12-12', 'FULL', 'RESIGNED', @pwd, 'EMPLOYEE', '1994-03-30', 'MALE',   '2023-07-08', NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_sales, @g_dae, @t_mem, @j_dist,'은지윤', 'hist026@peoplecore.kr', '010-2200-1026', 'EMP-HIST-026', '2022-01-30', 'FULL', 'RESIGNED', @pwd, 'EMPLOYEE', '1992-10-18', 'FEMALE', '2023-09-15', NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_dev,   @g_gwa, @t_mem, @j_it,  '도지훈', 'hist027@peoplecore.kr', '010-2200-1027', 'EMP-HIST-027', '2021-08-25', 'FULL', 'RESIGNED', @pwd, 'EMPLOYEE', '1989-05-22', 'MALE',   '2023-12-20', NULL, 1, 100, 'DC', FALSE),
        -- 2024 (Jan~Nov, 3명)
        (@cid, @d_hr,    @g_dae, @t_mem, @j_fin, '백서영', 'hist028@peoplecore.kr', '010-2200-1028', 'EMP-HIST-028', '2022-04-10', 'FULL', 'RESIGNED', @pwd, 'EMPLOYEE', '1993-08-14', 'FEMALE', '2024-03-12', NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_inf,   @g_emp, @t_mem, @j_it,  '노태진', 'hist029@peoplecore.kr', '010-2200-1029', 'EMP-HIST-029', '2021-11-15', 'FULL', 'RESIGNED', @pwd, 'EMPLOYEE', '1996-04-25', 'MALE',   '2024-06-25', NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_mkt,   @g_gwa, @t_mem, @j_etc, '홍시아', 'hist030@peoplecore.kr', '010-2200-1030', 'EMP-HIST-030', '2022-08-30', 'FULL', 'RESIGNED', @pwd, 'EMPLOYEE', '1990-01-08', 'FEMALE', '2024-10-30', NULL, 1, 100, 'DC', FALSE);


        -- =====================================================================
        -- 기본 워크그룹 일괄 매핑
        -- ---------------------------------------------------------------------
        -- 회사 생성 시 자동 시드되는 'DEFAULT' 그룹(9-18)에 work_group_id NULL 인 사원 전원 매핑.
        -- ※ PayrollService.getWageInfo() 가 emp.getWorkGroup() 을 null 체크 없이 사용하므로
        --   미매핑 시 급여대장 생성 단계에서 NPE 발생 → 본 UPDATE 필수.
        -- =====================================================================

        UPDATE employee
           SET work_group_id = (SELECT work_group_id FROM work_group
                                 WHERE company_id = @cid
                                   AND group_code  = 'DEFAULT'),
               work_group_assigned_at = NOW()
         WHERE company_id = @cid
           AND work_group_id IS NULL;


        -- =====================================================================
        -- emp_accounts  (사원 급여계좌)
        -- ---------------------------------------------------------------------
        --   98명 일괄 시드. 마지막 2명(EMP-2025-099, 100)은 의도적 미등록 →
        --   이체파일 다운로드 시 "계좌 미등록 사원" 검증 케이스 확보.
        --
        --   계좌번호 규칙: 110-{empNum 마지막 3자리 zero-pad 6자리}-01    (시드용 더미)
        --   은행:        모두 신한(088). 다양화하려면 CASE 추가 가능.
        --   예금주:      employee.emp_name 그대로 (오픈뱅킹 검증 통과 가정).
        -- =====================================================================

        INSERT INTO emp_accounts
          (emp_id, company_id, bank_code, bank_name, account_number, account_holder, created_at, updated_at)
        SELECT
          e.emp_id,
          e.company_id,
          '088',                                      -- 신한은행 코드
          '신한은행',
          CONCAT('110-', LPAD(SUBSTRING(e.emp_num, -3), 6, '0'), '-01'),
          e.emp_name,
          NOW(), NOW()
        FROM employee e
        WHERE e.company_id = @cid
          AND e.emp_num NOT IN ('EMP-2025-099', 'EMP-2025-100');   -- 의도적 미등록 2명 제외


        -- =====================================================================
        -- emp_retirement_account  (사원 퇴직급여계좌)
        -- ---------------------------------------------------------------------
        --   회사 설정이 DB_DC 이므로 사원별 retirement_type = DB or DC 혼재 가능.
        --   분배: emp_id 짝수 → DC, 홀수 → DB.
        --   - DC: pension_provider = 회사 운용사, account_number = 사원 본인 DC 계좌
        --   - DB: pension_provider = 회사 운용사, account_number = '' (회사 통합 운용)
        --
        --   퇴직자(EMP-2025-035, 076)는 별도 시나리오라 제외.
        -- =====================================================================

        INSERT INTO emp_retirement_account
          (emp_id, company_id, retirement_type, pension_provider, account_number, created_at, updated_at)
        SELECT
          e.emp_id,
          e.company_id,
          CASE WHEN e.emp_id % 2 = 0 THEN 'DC' ELSE 'DB' END,
          '미래에셋증권',
          CASE
            WHEN e.emp_id % 2 = 0
              THEN CONCAT('DC-', LPAD(SUBSTRING(e.emp_num, -3), 6, '0'))   -- DC: 본인 계좌
            ELSE ''                                                          -- DB: 회사 통합 운용
          END,
          NOW(), NOW()
        FROM employee e
        WHERE e.company_id = @cid
          AND e.emp_status != 'RESIGNED';        -- 퇴직자 제외


        -- employee.retirement_type 을 emp_retirement_account 와 동기화
        -- (회사 설정이 DB_DC 라 사원별 DB/DC 선택값을 employee 테이블에도 반영)
        UPDATE employee e
        JOIN emp_retirement_account era ON era.emp_id = e.emp_id
        SET e.retirement_type = era.retirement_type
        WHERE e.company_id = @cid;


        -- =====================================================================
        -- dependents_count 다양화 (간이세액표 분기 테스트용)
        -- ---------------------------------------------------------------------
        --   기본 1 → 일부만 2/4/5 로 변경. 분포:
        --     본인만(1)        : 70% (기본 그대로)
        --     본인+배우자(2)   : 15%
        --     배우자+자녀2(4)  : 10%
        --     배우자+자녀3(5)  :  5%
        -- =====================================================================

        UPDATE employee SET dependents_count = 2
         WHERE company_id = @cid
           AND emp_num IN ('EMP-2025-005', 'EMP-2025-015', 'EMP-2025-023', 'EMP-2025-058',
                           'EMP-2025-070', 'EMP-2025-088', 'EMP-2025-007', 'EMP-2025-016',
                           'EMP-2025-024', 'EMP-2025-027', 'EMP-2025-060', 'EMP-2025-073',
                           'EMP-2025-091', 'EMP-2025-092', 'EMP-2025-095');

        UPDATE employee SET dependents_count = 4
         WHERE company_id = @cid
           AND emp_num IN ('EMP-2025-001', 'EMP-2025-002', 'EMP-2025-003', 'EMP-2025-004',
                           'EMP-2025-022', 'EMP-2025-038', 'EMP-2025-064', 'EMP-2025-080',
                           'EMP-2025-081', 'EMP-2025-093');

        UPDATE employee SET dependents_count = 5
         WHERE company_id = @cid
           AND emp_num IN ('EMP-2025-018', 'EMP-2025-025', 'EMP-2025-074', 'EMP-2025-078',
                           'EMP-2025-090');


        -- =====================================================================
        -- resign  (사직원/퇴직처리 row)
        -- ---------------------------------------------------------------------
        --   추가 사원 20명 (EMP-2025-101~120) + 기존 RESIGNED 2명 (035, 076) = 22건.
        --
        --   컬럼: emp_id, grade_id, title_id, dept_id (모두 NOT NULL — employee 에서 SELECT 로 조달),
        --         doc_id (외부 결재 문서 ID, nullable),
        --         retire_status (ACTIVE / CONFIRMED / RESIGNED),
        --         resign_date (DATE), registered_date (DATE),
        --         processed_at (DATETIME, RESIGNED 만 NOW()),
        --         is_deleted (FALSE)
        --   ※ Resign 엔티티는 BaseTimeEntity 미상속 → created_at/updated_at 컬럼 없음.
        --
        --   doc_id 매핑 (외부 결재시스템 doc 상태 구분용):
        --     1001~1005 : (a) PENDING — 결재 진행중
        --     2001~2005 : (b) APPROVED — 결재 승인 났으나 인사담당자 퇴직처리 미클릭
        --     3001~3005 : (c) APPROVED — 퇴직처리 클릭됨 (CONFIRMED, 퇴직일 미래)
        --     4001~4005 : (d) APPROVED — 완전 퇴직 처리 완료 (RESIGNED, 스케줄러 처리됨)
        --     4101~4102 : 기존 RESIGNED 2명 (소급 등록)
        -- =====================================================================

        INSERT INTO resign (
          emp_id, grade_id, title_id, dept_id,
          doc_id, retire_status, resign_date, registered_date, processed_at,
          is_deleted
        )
        SELECT
          e.emp_id, e.grade_id, e.title_id, e.dept_id,
          -- doc_id: 케이스별 식별 ID
          CASE e.emp_num
            WHEN 'EMP-2025-101' THEN 1001  WHEN 'EMP-2025-102' THEN 1002
            WHEN 'EMP-2025-103' THEN 1003  WHEN 'EMP-2025-104' THEN 1004  WHEN 'EMP-2025-105' THEN 1005
            WHEN 'EMP-2025-106' THEN 2001  WHEN 'EMP-2025-107' THEN 2002
            WHEN 'EMP-2025-108' THEN 2003  WHEN 'EMP-2025-109' THEN 2004  WHEN 'EMP-2025-110' THEN 2005
            WHEN 'EMP-2025-111' THEN 3001  WHEN 'EMP-2025-112' THEN 3002
            WHEN 'EMP-2025-113' THEN 3003  WHEN 'EMP-2025-114' THEN 3004  WHEN 'EMP-2025-115' THEN 3005
            WHEN 'EMP-2025-116' THEN 4001  WHEN 'EMP-2025-117' THEN 4002
            WHEN 'EMP-2025-118' THEN 4003  WHEN 'EMP-2025-119' THEN 4004  WHEN 'EMP-2025-120' THEN 4005
            WHEN 'EMP-2025-035' THEN 4101  WHEN 'EMP-2025-076' THEN 4102
          END,
          -- retire_status
          CASE
            WHEN e.emp_num BETWEEN 'EMP-2025-101' AND 'EMP-2025-110' THEN 'ACTIVE'
            WHEN e.emp_num BETWEEN 'EMP-2025-111' AND 'EMP-2025-115' THEN 'CONFIRMED'
            WHEN e.emp_num BETWEEN 'EMP-2025-116' AND 'EMP-2025-120' THEN 'RESIGNED'
            WHEN e.emp_num IN ('EMP-2025-035', 'EMP-2025-076')         THEN 'RESIGNED'
          END,
          -- resign_date (퇴직(예정)일)
          CASE e.emp_num
            WHEN 'EMP-2025-101' THEN '2026-09-30'  WHEN 'EMP-2025-102' THEN '2026-10-15'
            WHEN 'EMP-2025-103' THEN '2026-11-30'  WHEN 'EMP-2025-104' THEN '2026-12-31'
            WHEN 'EMP-2025-105' THEN '2027-01-31'
            WHEN 'EMP-2025-106' THEN '2026-07-31'  WHEN 'EMP-2025-107' THEN '2026-08-31'
            WHEN 'EMP-2025-108' THEN '2026-09-15'  WHEN 'EMP-2025-109' THEN '2026-10-31'
            WHEN 'EMP-2025-110' THEN '2026-11-30'
            WHEN 'EMP-2025-111' THEN '2026-06-30'  WHEN 'EMP-2025-112' THEN '2026-07-31'
            WHEN 'EMP-2025-113' THEN '2026-08-15'  WHEN 'EMP-2025-114' THEN '2026-09-30'
            WHEN 'EMP-2025-115' THEN '2026-12-31'
            -- (d) 완전퇴직: emp_resign 과 동일
            WHEN 'EMP-2025-116' THEN '2026-02-15'  WHEN 'EMP-2025-117' THEN '2025-09-30'
            WHEN 'EMP-2025-118' THEN '2025-12-31'  WHEN 'EMP-2025-119' THEN '2026-03-15'
            WHEN 'EMP-2025-120' THEN '2026-01-31'
            -- 기존 RESIGNED 2명
            WHEN 'EMP-2025-035' THEN '2024-12-31'  WHEN 'EMP-2025-076' THEN '2025-03-15'
          END,
          -- registered_date (사직원 신청일 = resign_date 한참 전)
          CASE e.emp_num
            WHEN 'EMP-2025-101' THEN '2026-05-02'  WHEN 'EMP-2025-102' THEN '2026-05-04'
            WHEN 'EMP-2025-103' THEN '2026-05-03'  WHEN 'EMP-2025-104' THEN '2026-05-05'
            WHEN 'EMP-2025-105' THEN '2026-05-06'
            WHEN 'EMP-2025-106' THEN '2026-04-20'  WHEN 'EMP-2025-107' THEN '2026-05-01'
            WHEN 'EMP-2025-108' THEN '2026-04-30'  WHEN 'EMP-2025-109' THEN '2026-04-25'
            WHEN 'EMP-2025-110' THEN '2026-04-15'
            WHEN 'EMP-2025-111' THEN '2026-04-10'  WHEN 'EMP-2025-112' THEN '2026-04-22'
            WHEN 'EMP-2025-113' THEN '2026-04-18'  WHEN 'EMP-2025-114' THEN '2026-03-25'
            WHEN 'EMP-2025-115' THEN '2026-04-08'
            WHEN 'EMP-2025-116' THEN '2025-12-15'  WHEN 'EMP-2025-117' THEN '2025-08-05'
            WHEN 'EMP-2025-118' THEN '2025-11-10'  WHEN 'EMP-2025-119' THEN '2026-02-01'
            WHEN 'EMP-2025-120' THEN '2025-12-20'
            WHEN 'EMP-2025-035' THEN '2024-11-15'  WHEN 'EMP-2025-076' THEN '2025-02-10'
          END,
          -- processed_at: RESIGNED 만 (스케줄러가 resign_date 시점에 처리)
          CASE
            WHEN e.emp_num = 'EMP-2025-116' THEN '2026-02-15 09:00:00'
            WHEN e.emp_num = 'EMP-2025-117' THEN '2025-09-30 09:00:00'
            WHEN e.emp_num = 'EMP-2025-118' THEN '2025-12-31 09:00:00'
            WHEN e.emp_num = 'EMP-2025-119' THEN '2026-03-15 09:00:00'
            WHEN e.emp_num = 'EMP-2025-120' THEN '2026-01-31 09:00:00'
            WHEN e.emp_num = 'EMP-2025-035' THEN '2024-12-31 09:00:00'
            WHEN e.emp_num = 'EMP-2025-076' THEN '2025-03-15 09:00:00'
            ELSE NULL
          END,
          FALSE
        FROM employee e
        WHERE e.company_id = @cid
          AND e.emp_num IN (
            'EMP-2025-101','EMP-2025-102','EMP-2025-103','EMP-2025-104','EMP-2025-105',
            'EMP-2025-106','EMP-2025-107','EMP-2025-108','EMP-2025-109','EMP-2025-110',
            'EMP-2025-111','EMP-2025-112','EMP-2025-113','EMP-2025-114','EMP-2025-115',
            'EMP-2025-116','EMP-2025-117','EMP-2025-118','EMP-2025-119','EMP-2025-120',
            'EMP-2025-035','EMP-2025-076'
          );


        -- =====================================================================
        -- 더미 프로필 이미지 URL 매핑 — 앞 128명 (MinIO seed와 1:1 대응)
        -- ---------------------------------------------------------------------
        -- 선행 조건:
        --   1) MinIO 버킷 'peoplecore-profile'에 128개 객체가 적재되어 있어야 함
        --      (docker-compose의 minio-init 서비스가 처리)
        --   2) 객체 키 패턴: emp-{emp_id}/{emp_num}.jpg
        --   3) 본 SQL은 @cid 변수가 살아있는 같은 세션에서 실행되어야 함
        --
        -- 매핑 결과:
        --   emp_num='EMP-2025-001', emp_id=1
        --     → emp_profile_image_url='/employee/profile-images/emp-1/EMP-2025-001.jpg'
        --
        -- ⚠️ emp_id 의존성:
        --   본 UPDATE는 INSERT 시점에 emp_id가 1~28로 깨끗하게 할당된 상태를 가정.
        --   (auto_increment 1부터 시작하는 신규 시드 환경)
        --   기존 데이터가 있어 emp_id가 어긋나면 MinIO 객체 키와 불일치 → 404.
        --   → 검증: SELECT MIN(emp_id), MAX(emp_id) FROM employee WHERE emp_num
        --           BETWEEN 'EMP-2025-001' AND 'EMP-2025-028';
        --   → 1, 28 이 아니면 본 UPDATE 직전에 MinIO 적재 키도 같이 조정 필요.
        -- =====================================================================
        UPDATE employee
            SET emp_profile_image_url = CONCAT('/employee/profile-images/seed/', emp_num, '.jpg')
            WHERE company_id = @cid
            AND emp_num BETWEEN 'EMP-2025-001' AND 'EMP-2025-128';


        -- =====================================================================
        -- [추가 시드] 디자인팀 사원 3명 (소규모팀 시연용 — undersizedTeams)
        -- ---------------------------------------------------------------------
        -- 평가 시즌 자동 산정 시 teamSize=3 < minTeamSize=5 → 보정 참고사항에 노출
        -- =====================================================================
        INSERT INTO employee (
          company_id, dept_id, grade_id, title_id, insurance_job_types,
          emp_name, emp_email, emp_phone, emp_num,
          emp_hire_date, emp_type, emp_status, emp_password, emp_role,
          emp_birth_date, emp_gender,
          emp_resign, contract_end_date,
          dependents_count, tax_rate_option, retirement_type, must_change_password
        ) VALUES
        (@cid, @d_design, @g_dae, @t_mem, @j_etc, '윤은서', 'emp180@peoplecore.kr', '010-2180-4180', 'EMP-2025-180', '2024-03-15', 'FULL', 'ACTIVE', @pwd, 'EMPLOYEE', '1995-04-22', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_design, @g_emp, @t_mem, @j_etc, '오태성', 'emp181@peoplecore.kr', '010-2181-4181', 'EMP-2025-181', '2024-09-08', 'FULL', 'ACTIVE', @pwd, 'EMPLOYEE', '2000-11-15', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_design, @g_emp, @t_mem, @j_etc, '한가람', 'emp182@peoplecore.kr', '010-2182-4182', 'EMP-2025-182', '2025-02-20', 'FULL', 'ACTIVE', @pwd, 'EMPLOYEE', '2001-08-03', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE);

        -- =====================================================================
        -- [추가 시드] 2026년 입사자 (월별 추이 차트용 — 2025년 1~5월 분포 미러링)
        -- ---------------------------------------------------------------------
        -- 1월 2 / 2월 3 / 3월 1 / 4월 2 / 5월 2 = 10명
        -- =====================================================================
        INSERT INTO employee (
          company_id, dept_id, grade_id, title_id, insurance_job_types,
          emp_name, emp_email, emp_phone, emp_num,
          emp_hire_date, emp_type, emp_status, emp_password, emp_role,
          emp_birth_date, emp_gender,
          emp_resign, contract_end_date,
          dependents_count, tax_rate_option, retirement_type, must_change_password
        ) VALUES
        -- 1월
        (@cid, @d_dev,   @g_emp, @t_mem, @j_it,  '강민찬', 'emp183@peoplecore.kr', '010-2183-4183', 'EMP-2025-183', '2026-01-12', 'FULL', 'ACTIVE', @pwd, 'EMPLOYEE', '2001-03-15', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_sales, @g_emp, @t_mem, @j_dist,'박서연', 'emp184@peoplecore.kr', '010-2184-4184', 'EMP-2025-184', '2026-01-25', 'FULL', 'ACTIVE', @pwd, 'EMPLOYEE', '2002-07-22', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        -- 2월
        (@cid, @d_hr,    @g_emp, @t_mem, @j_fin, '이지호', 'emp185@peoplecore.kr', '010-2185-4185', 'EMP-2025-185', '2026-02-05', 'FULL', 'ACTIVE', @pwd, 'EMPLOYEE', '2001-10-08', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_dev,   @g_emp, @t_mem, @j_it,  '최예원', 'emp186@peoplecore.kr', '010-2186-4186', 'EMP-2025-186', '2026-02-14', 'FULL', 'ACTIVE', @pwd, 'EMPLOYEE', '2002-04-30', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_inf,   @g_emp, @t_mem, @j_it,  '김도현', 'emp187@peoplecore.kr', '010-2187-4187', 'EMP-2025-187', '2026-02-26', 'FULL', 'ACTIVE', @pwd, 'EMPLOYEE', '2001-11-12', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        -- 3월
        (@cid, @d_mkt,   @g_emp, @t_mem, @j_etc, '정유나', 'emp188@peoplecore.kr', '010-2188-4188', 'EMP-2025-188', '2026-03-19', 'FULL', 'ACTIVE', @pwd, 'EMPLOYEE', '2002-09-05', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        -- 4월
        (@cid, @d_dev,   @g_emp, @t_mem, @j_it,  '오승민', 'emp189@peoplecore.kr', '010-2189-4189', 'EMP-2025-189', '2026-04-09', 'FULL', 'ACTIVE', @pwd, 'EMPLOYEE', '2001-06-18', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_fin,   @g_emp, @t_mem, @j_fin, '조서영', 'emp190@peoplecore.kr', '010-2190-4190', 'EMP-2025-190', '2026-04-22', 'FULL', 'ACTIVE', @pwd, 'EMPLOYEE', '2002-12-30', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE),
        -- 5월
        (@cid, @d_sales, @g_emp, @t_mem, @j_dist,'윤지훈', 'emp191@peoplecore.kr', '010-2191-4191', 'EMP-2025-191', '2026-05-04', 'FULL', 'ACTIVE', @pwd, 'EMPLOYEE', '2003-02-15', 'MALE',   NULL, NULL, 1, 100, 'DC', FALSE),
        (@cid, @d_dev,   @g_emp, @t_mem, @j_it,  '한지원', 'emp192@peoplecore.kr', '010-2192-4192', 'EMP-2025-192', '2026-05-07', 'FULL', 'ACTIVE', @pwd, 'EMPLOYEE', '2002-08-25', 'FEMALE', NULL, NULL, 1, 100, 'DC', FALSE);

        -- =====================================================================
        -- [검증 쿼리] INSERT 결과 카운트
        -- =====================================================================
        -- SELECT '총 사원' AS metric, COUNT(*) AS cnt FROM employee WHERE company_id = @cid;             -- 100
        --
        -- SELECT d.dept_name, COUNT(*) AS cnt
        --   FROM employee e JOIN department d ON e.dept_id = d.dept_id
        --  WHERE e.company_id = @cid
        --  GROUP BY d.dept_id, d.dept_name ORDER BY d.dept_id;
        -- 예상: 임원실 4 / 인사 10 / 재무 8 / 개발 35 / 인프라 12 / 영업 18 / 마케팅 13
        --
        -- SELECT g.grade_name, COUNT(*) AS cnt
        --   FROM employee e JOIN grade g ON e.grade_id = g.grade_id
        --  WHERE e.company_id = @cid
        --  GROUP BY g.grade_id, g.grade_name ORDER BY g.grade_order;
        -- 예상: 사원 35 / 대리 25 / 과장 20 / 차장 12 / 부장 4 / 이사 4
        --
        -- SELECT emp_status, COUNT(*) FROM employee WHERE company_id = @cid GROUP BY emp_status;
        -- 예상: ACTIVE 95 / ON_LEAVE 3 / RESIGNED 2
        --
        -- SELECT emp_type, COUNT(*) FROM employee WHERE company_id = @cid GROUP BY emp_type;
        -- 예상: FULL 90 / CONTRACT 10
        --
        -- SELECT emp_role, COUNT(*) FROM employee WHERE company_id = @cid GROUP BY emp_role;
        -- 예상: HR_SUPER_ADMIN 1 / HR_ADMIN 2 / EMPLOYEE 97
