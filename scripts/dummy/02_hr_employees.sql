
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
(@cid, @d_exec,  @g_isa, @t_ceo,  @j_fin, '김민준', 'emp001@peoplecore.kr', '010-2001-4001', 'EMP-2025-001', '2010-03-02', 'FULL', 'ACTIVE', @pwd, 'HR_SUPER_ADMIN', '1965-03-15', 'MALE',   NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_exec,  @g_isa, @t_head, @j_fin, '이서연', 'emp002@peoplecore.kr', '010-2002-4002', 'EMP-2025-002', '2012-06-18', 'FULL', 'ACTIVE', @pwd, 'EMPLOYEE',       '1968-08-22', 'FEMALE', NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_exec,  @g_isa, @t_head, @j_fin, '박지호', 'emp003@peoplecore.kr', '010-2003-4003', 'EMP-2025-003', '2013-09-04', 'FULL', 'ACTIVE', @pwd, 'EMPLOYEE',       '1970-11-05', 'MALE',   NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_exec,  @g_isa, @t_head, @j_fin, '정수아', 'emp004@peoplecore.kr', '010-2004-4004', 'EMP-2025-004', '2014-11-22', 'FULL', 'ACTIVE', @pwd, 'EMPLOYEE',       '1972-06-18', 'FEMALE', NULL, NULL, 1, 100, 2, FALSE),
-- ───── 인사팀 (HR) ─────
(@cid, @d_hr,    @g_bu,  @t_lead, @j_fin, '최도윤', 'emp005@peoplecore.kr', '010-2005-4005', 'EMP-2025-005', '2014-04-15', 'FULL', 'ACTIVE', @pwd, 'HR_ADMIN', '1975-09-12', 'MALE',   NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_hr,    @g_cha, @t_mem,  @j_fin, '강시우', 'emp006@peoplecore.kr', '010-2006-4006', 'EMP-2025-006', '2018-02-26', 'FULL', 'ACTIVE', @pwd, 'HR_ADMIN', '1981-04-28', 'FEMALE', NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_hr,    @g_gwa, @t_mem,  @j_fin, '윤하준', 'emp007@peoplecore.kr', '010-2007-4007', 'EMP-2025-007', '2020-04-15', 'FULL', 'ACTIVE', @pwd, 'EMPLOYEE', '1985-07-15', 'MALE',   NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_hr,    @g_gwa, @t_mem,  @j_fin, '장지유', 'emp008@peoplecore.kr', '010-2008-4008', 'EMP-2025-008', '2021-07-22', 'FULL', 'ACTIVE', @pwd, 'EMPLOYEE', '1986-12-03', 'FEMALE', NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_hr,    @g_dae, @t_mem,  @j_fin, '임예준', 'emp009@peoplecore.kr', '010-2009-4009', 'EMP-2025-009', '2022-05-13', 'CONTRACT', 'ACTIVE', @pwd, 'EMPLOYEE', '1989-05-20', 'MALE',   NULL, '2027-12-31', 1, 100, 2, FALSE),
(@cid, @d_hr,    @g_dae, @t_mem,  @j_fin, '한서윤', 'emp010@peoplecore.kr', '010-2010-4010', 'EMP-2025-010', '2023-02-08', 'FULL', 'ACTIVE', @pwd, 'EMPLOYEE', '1990-08-14', 'FEMALE', NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_hr,    @g_emp, @t_mem,  @j_fin, '오현우', 'emp011@peoplecore.kr', '010-2011-4011', 'EMP-2025-011', '2023-05-26', 'FULL', 'ACTIVE', @pwd, 'EMPLOYEE', '1996-02-09', 'MALE',   NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_hr,    @g_emp, @t_mem,  @j_fin, '신유진', 'emp012@peoplecore.kr', '010-2012-4012', 'EMP-2025-012', '2024-01-08', 'FULL', 'ACTIVE', @pwd, 'EMPLOYEE', '1998-06-22', 'FEMALE', NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_hr,    @g_emp, @t_mem,  @j_fin, '권지훈', 'emp013@peoplecore.kr', '010-2013-4013', 'EMP-2025-013', '2024-08-19', 'FULL', 'ACTIVE', @pwd, 'EMPLOYEE', '1999-10-30', 'MALE',   NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_hr,    @g_emp, @t_mem,  @j_fin, '조서현', 'emp014@peoplecore.kr', '010-2014-4014', 'EMP-2025-014', '2025-02-04', 'FULL', 'ACTIVE', @pwd, 'EMPLOYEE', '2000-03-17', 'FEMALE', NULL, NULL, 1, 100, 2, FALSE),
-- ───── 재무팀 (FIN) ─────
(@cid, @d_fin,   @g_bu,  @t_lead, @j_fin, '백건우', 'emp015@peoplecore.kr', '010-2015-4015', 'EMP-2025-015', '2015-08-20', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1976-11-08', 'MALE',   NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_fin,   @g_cha, @t_mem,  @j_fin, '송하은', 'emp016@peoplecore.kr', '010-2016-4016', 'EMP-2025-016', '2019-05-13', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1981-07-25', 'FEMALE', NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_fin,   @g_gwa, @t_mem,  @j_fin, '노지원', 'emp017@peoplecore.kr', '010-2017-4017', 'EMP-2025-017', '2020-09-08', 'FULL', 'ON_LEAVE', @pwd, 'EMPLOYEE', '1986-04-13', 'MALE',   NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_fin,   @g_dae, @t_mem,  @j_fin, '홍시현', 'emp018@peoplecore.kr', '010-2018-4018', 'EMP-2025-018', '2022-09-26', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1990-09-30', 'FEMALE', NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_fin,   @g_dae, @t_mem,  @j_fin, '안주원', 'emp019@peoplecore.kr', '010-2019-4019', 'EMP-2025-019', '2023-04-12', 'CONTRACT', 'ACTIVE', @pwd, 'EMPLOYEE', '1991-02-18', 'MALE',   NULL, '2027-12-31', 1, 100, 2, FALSE),
(@cid, @d_fin,   @g_emp, @t_mem,  @j_fin, '류다은', 'emp020@peoplecore.kr', '010-2020-4020', 'EMP-2025-020', '2023-07-13', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1996-12-07', 'FEMALE', NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_fin,   @g_emp, @t_mem,  @j_fin, '배현준', 'emp021@peoplecore.kr', '010-2021-4021', 'EMP-2025-021', '2024-04-22', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1998-05-25', 'MALE',   NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_fin,   @g_emp, @t_mem,  @j_fin, '서지안', 'emp022@peoplecore.kr', '010-2022-4022', 'EMP-2025-022', '2025-01-15', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '2000-08-11', 'FEMALE', NULL, NULL, 1, 100, 2, FALSE),
-- ───── 개발팀 (DEV) ─────
(@cid, @d_dev,   @g_bu,  @t_lead, @j_it,  '남도현', 'emp023@peoplecore.kr', '010-2023-4023', 'EMP-2025-023', '2014-11-08', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1977-04-08', 'MALE',   NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_dev,   @g_cha, @t_mem,  @j_it,  '문하윤', 'emp024@peoplecore.kr', '010-2024-4024', 'EMP-2025-024', '2018-07-09', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1980-09-18', 'FEMALE', NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_dev,   @g_cha, @t_mem,  @j_it,  '양준서', 'emp025@peoplecore.kr', '010-2025-4025', 'EMP-2025-025', '2019-03-25', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1982-12-05', 'MALE',   NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_dev,   @g_cha, @t_mem,  @j_it,  '진서영', 'emp026@peoplecore.kr', '010-2026-4026', 'EMP-2025-026', '2020-01-15', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1983-06-22', 'FEMALE', NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_dev,   @g_gwa, @t_mem,  @j_it,  '차예린', 'emp027@peoplecore.kr', '010-2027-4027', 'EMP-2025-027', '2019-08-26', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1984-02-14', 'FEMALE', NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_dev,   @g_gwa, @t_mem,  @j_it,  '구민서', 'emp028@peoplecore.kr', '010-2028-4028', 'EMP-2025-028', '2020-02-14', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1985-05-30', 'MALE',   NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_dev,   @g_gwa, @t_mem,  @j_it,  '표하윤', 'emp029@peoplecore.kr', '010-2029-4029', 'EMP-2025-029', '2020-06-30', 'CONTRACT', 'ACTIVE', @pwd, 'EMPLOYEE', '1985-11-08', 'FEMALE', NULL, '2027-12-31', 1, 100, 2, FALSE),
(@cid, @d_dev,   @g_gwa, @t_mem,  @j_it,  '은지호', 'emp030@peoplecore.kr', '010-2030-4030', 'EMP-2025-030', '2020-11-19', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1986-08-19', 'MALE',   NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_dev,   @g_gwa, @t_mem,  @j_it,  '원유나', 'emp031@peoplecore.kr', '010-2031-4031', 'EMP-2025-031', '2021-04-08', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1987-03-26', 'FEMALE', NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_dev,   @g_gwa, @t_mem,  @j_it,  '추서준', 'emp032@peoplecore.kr', '010-2032-4032', 'EMP-2025-032', '2021-09-15', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1987-10-12', 'MALE',   NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_dev,   @g_gwa, @t_mem,  @j_it,  '변하준', 'emp033@peoplecore.kr', '010-2033-4033', 'EMP-2025-033', '2022-01-25', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1988-04-05', 'MALE',   NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_dev,   @g_dae, @t_mem,  @j_it,  '도예원', 'emp034@peoplecore.kr', '010-2034-4034', 'EMP-2025-034', '2021-11-15', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1989-01-22', 'FEMALE', NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_dev,   @g_dae, @t_mem,  @j_it,  '명소율', 'emp035@peoplecore.kr', '010-2035-4035', 'EMP-2025-035', '2022-03-08', 'FULL', 'RESIGNED', @pwd, 'EMPLOYEE', '1989-07-15', 'FEMALE', '2024-12-31', NULL, 1, 100, 2, FALSE),
(@cid, @d_dev,   @g_dae, @t_mem,  @j_it,  '형지유', 'emp036@peoplecore.kr', '010-2036-4036', 'EMP-2025-036', '2022-06-22', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1990-04-08', 'FEMALE', NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_dev,   @g_dae, @t_mem,  @j_it,  '사하린', 'emp037@peoplecore.kr', '010-2037-4037', 'EMP-2025-037', '2022-09-30', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1990-11-19', 'FEMALE', NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_dev,   @g_dae, @t_mem,  @j_it,  '두민호', 'emp038@peoplecore.kr', '010-2038-4038', 'EMP-2025-038', '2023-01-18', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1991-02-26', 'MALE',   NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_dev,   @g_dae, @t_mem,  @j_it,  '황건우', 'emp039@peoplecore.kr', '010-2039-4039', 'EMP-2025-039', '2023-05-04', 'CONTRACT', 'ACTIVE', @pwd, 'EMPLOYEE', '1991-09-13', 'MALE',   NULL, '2027-12-31', 1, 100, 2, FALSE),
(@cid, @d_dev,   @g_dae, @t_mem,  @j_it,  '곽지수', 'emp040@peoplecore.kr', '010-2040-4040', 'EMP-2025-040', '2023-08-25', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1992-05-02', 'FEMALE', NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_dev,   @g_dae, @t_mem,  @j_it,  '탁윤서', 'emp041@peoplecore.kr', '010-2041-4041', 'EMP-2025-041', '2023-12-11', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1992-12-14', 'MALE',   NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_dev,   @g_dae, @t_mem,  @j_it,  '위주아', 'emp042@peoplecore.kr', '010-2042-4042', 'EMP-2025-042', '2024-03-19', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1993-06-25', 'FEMALE', NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_dev,   @g_dae, @t_mem,  @j_it,  '어재현', 'emp043@peoplecore.kr', '010-2043-4043', 'EMP-2025-043', '2024-07-02', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1993-10-30', 'MALE',   NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_dev,   @g_emp, @t_mem,  @j_it,  '옥도훈', 'emp044@peoplecore.kr', '010-2044-4044', 'EMP-2025-044', '2023-02-15', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1995-03-18', 'MALE',   NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_dev,   @g_emp, @t_mem,  @j_it,  '마지원', 'emp045@peoplecore.kr', '010-2045-4045', 'EMP-2025-045', '2023-05-30', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1996-07-22', 'FEMALE', NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_dev,   @g_emp, @t_mem,  @j_it,  '부태민', 'emp046@peoplecore.kr', '010-2046-4046', 'EMP-2025-046', '2023-08-12', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1996-12-04', 'MALE',   NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_dev,   @g_emp, @t_mem,  @j_it,  '호윤재', 'emp047@peoplecore.kr', '010-2047-4047', 'EMP-2025-047', '2023-11-25', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1997-05-29', 'MALE',   NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_dev,   @g_emp, @t_mem,  @j_it,  '양수빈', 'emp048@peoplecore.kr', '010-2048-4048', 'EMP-2025-048', '2024-02-08', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1997-09-15', 'FEMALE', NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_dev,   @g_emp, @t_mem,  @j_it,  '갈태양', 'emp049@peoplecore.kr', '010-2049-4049', 'EMP-2025-049', '2024-04-22', 'CONTRACT', 'ACTIVE', @pwd, 'EMPLOYEE', '1998-02-26', 'MALE',   NULL, '2027-12-31', 1, 100, 2, FALSE),
(@cid, @d_dev,   @g_emp, @t_mem,  @j_it,  '용예성', 'emp050@peoplecore.kr', '010-2050-4050', 'EMP-2025-050', '2024-07-15', 'FULL', 'ON_LEAVE', @pwd, 'EMPLOYEE', '1998-08-11', 'MALE',   NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_dev,   @g_emp, @t_mem,  @j_it,  '엄정민', 'emp051@peoplecore.kr', '010-2051-4051', 'EMP-2025-051', '2024-09-30', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1999-04-19', 'MALE',   NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_dev,   @g_emp, @t_mem,  @j_it,  '음하경', 'emp052@peoplecore.kr', '010-2052-4052', 'EMP-2025-052', '2024-12-18', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1999-10-08', 'FEMALE', NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_dev,   @g_emp, @t_mem,  @j_it,  '화서윤', 'emp053@peoplecore.kr', '010-2053-4053', 'EMP-2025-053', '2025-01-09', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '2000-01-23', 'FEMALE', NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_dev,   @g_emp, @t_mem,  @j_it,  '시예진', 'emp054@peoplecore.kr', '010-2054-4054', 'EMP-2025-054', '2025-02-25', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '2000-06-15', 'FEMALE', NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_dev,   @g_emp, @t_mem,  @j_it,  '아도윤', 'emp055@peoplecore.kr', '010-2055-4055', 'EMP-2025-055', '2025-04-14', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '2001-03-12', 'MALE',   NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_dev,   @g_emp, @t_mem,  @j_it,  '매지현', 'emp056@peoplecore.kr', '010-2056-4056', 'EMP-2025-056', '2025-05-30', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '2001-11-28', 'FEMALE', NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_dev,   @g_emp, @t_mem,  @j_it,  '하태경', 'emp057@peoplecore.kr', '010-2057-4057', 'EMP-2025-057', '2025-08-22', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '2002-07-04', 'MALE',   NULL, NULL, 1, 100, 2, FALSE),
-- ───── 인프라팀 (INF) ─────
(@cid, @d_inf,   @g_cha, @t_lead, @j_it,  '빈주영', 'emp058@peoplecore.kr', '010-2058-4058', 'EMP-2025-058', '2017-09-11', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1980-08-15', 'MALE',   NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_inf,   @g_cha, @t_mem,  @j_it,  '함채원', 'emp059@peoplecore.kr', '010-2059-4059', 'EMP-2025-059', '2019-10-08', 'CONTRACT', 'ACTIVE', @pwd, 'EMPLOYEE', '1982-12-22', 'FEMALE', NULL, '2027-12-31', 1, 100, 2, FALSE),
(@cid, @d_inf,   @g_gwa, @t_mem,  @j_it,  '봉승호', 'emp060@peoplecore.kr', '010-2060-4060', 'EMP-2025-060', '2020-08-13', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1985-04-08', 'MALE',   NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_inf,   @g_gwa, @t_mem,  @j_it,  '방연우', 'emp061@peoplecore.kr', '010-2061-4061', 'EMP-2025-061', '2021-05-24', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1986-09-19', 'FEMALE', NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_inf,   @g_gwa, @t_mem,  @j_it,  '라하늘', 'emp062@peoplecore.kr', '010-2062-4062', 'EMP-2025-062', '2022-02-07', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1987-12-30', 'FEMALE', NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_inf,   @g_dae, @t_mem,  @j_it,  '모은채', 'emp063@peoplecore.kr', '010-2063-4063', 'EMP-2025-063', '2022-12-08', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1989-02-15', 'FEMALE', NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_inf,   @g_dae, @t_mem,  @j_it,  '단지애', 'emp064@peoplecore.kr', '010-2064-4064', 'EMP-2025-064', '2023-06-19', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1990-07-23', 'FEMALE', NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_inf,   @g_dae, @t_mem,  @j_it,  '우민혁', 'emp065@peoplecore.kr', '010-2065-4065', 'EMP-2025-065', '2024-02-26', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1991-10-05', 'MALE',   NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_inf,   @g_emp, @t_mem,  @j_it,  '가서영', 'emp066@peoplecore.kr', '010-2066-4066', 'EMP-2025-066', '2023-09-26', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1996-05-12', 'FEMALE', NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_inf,   @g_emp, @t_mem,  @j_it,  '비주현', 'emp067@peoplecore.kr', '010-2067-4067', 'EMP-2025-067', '2024-05-13', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1998-08-26', 'FEMALE', NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_inf,   @g_emp, @t_mem,  @j_it,  '그하영', 'emp068@peoplecore.kr', '010-2068-4068', 'EMP-2025-068', '2024-11-22', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1999-11-14', 'FEMALE', NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_inf,   @g_emp, @t_mem,  @j_it,  '차도엽', 'emp069@peoplecore.kr', '010-2069-4069', 'EMP-2025-069', '2025-03-08', 'CONTRACT', 'ACTIVE', @pwd, 'EMPLOYEE', '2000-03-08', 'MALE',   NULL, '2027-12-31', 1, 100, 2, FALSE),
-- ───── 영업팀 (SALES) ─────
(@cid, @d_sales, @g_bu,  @t_lead, @j_dist,'즈경수', 'emp070@peoplecore.kr', '010-2070-4070', 'EMP-2025-070', '2015-06-12', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1976-08-24', 'MALE',   NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_sales, @g_cha, @t_mem,  @j_dist,'선재민', 'emp071@peoplecore.kr', '010-2071-4071', 'EMP-2025-071', '2018-12-04', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1980-11-30', 'MALE',   NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_sales, @g_cha, @t_mem,  @j_dist,'라윤혁', 'emp072@peoplecore.kr', '010-2072-4072', 'EMP-2025-072', '2019-08-19', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1982-05-14', 'MALE',   NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_sales, @g_gwa, @t_mem,  @j_dist,'성다현', 'emp073@peoplecore.kr', '010-2073-4073', 'EMP-2025-073', '2019-12-02', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1984-09-22', 'FEMALE', NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_sales, @g_gwa, @t_mem,  @j_dist,'도연수', 'emp074@peoplecore.kr', '010-2074-4074', 'EMP-2025-074', '2020-07-19', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1985-12-08', 'FEMALE', NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_sales, @g_gwa, @t_mem,  @j_dist,'설지영', 'emp075@peoplecore.kr', '010-2075-4075', 'EMP-2025-075', '2021-03-08', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1986-04-15', 'FEMALE', NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_sales, @g_gwa, @t_mem,  @j_dist,'류태현', 'emp076@peoplecore.kr', '010-2076-4076', 'EMP-2025-076', '2021-10-25', 'FULL', 'RESIGNED', @pwd, 'EMPLOYEE', '1987-07-29', 'MALE', '2025-03-15', NULL, 1, 100, 2, FALSE),
(@cid, @d_sales, @g_gwa, @t_mem,  @j_dist,'류수민', 'emp077@peoplecore.kr', '010-2077-4077', 'EMP-2025-077', '2022-05-14', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1988-02-11', 'MALE',   NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_sales, @g_dae, @t_mem,  @j_dist,'정채린', 'emp078@peoplecore.kr', '010-2078-4078', 'EMP-2025-078', '2022-08-30', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1989-06-25', 'FEMALE', NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_sales, @g_dae, @t_mem,  @j_dist,'송예지', 'emp079@peoplecore.kr', '010-2079-4079', 'EMP-2025-079', '2023-04-15', 'CONTRACT', 'ACTIVE', @pwd, 'EMPLOYEE', '1990-08-18', 'FEMALE', NULL, '2027-12-31', 1, 100, 2, FALSE),
(@cid, @d_sales, @g_dae, @t_mem,  @j_dist,'윤지민', 'emp080@peoplecore.kr', '010-2080-4080', 'EMP-2025-080', '2023-11-22', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1991-12-04', 'MALE',   NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_sales, @g_dae, @t_mem,  @j_dist,'이주환', 'emp081@peoplecore.kr', '010-2081-4081', 'EMP-2025-081', '2024-05-06', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1992-04-19', 'MALE',   NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_sales, @g_emp, @t_mem,  @j_dist,'김서아', 'emp082@peoplecore.kr', '010-2082-4082', 'EMP-2025-082', '2023-04-19', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1996-08-13', 'FEMALE', NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_sales, @g_emp, @t_mem,  @j_dist,'박태우', 'emp083@peoplecore.kr', '010-2083-4083', 'EMP-2025-083', '2023-08-26', 'FULL', 'ON_LEAVE', @pwd, 'EMPLOYEE', '1997-11-25', 'MALE',   NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_sales, @g_emp, @t_mem,  @j_dist,'최예나', 'emp084@peoplecore.kr', '010-2084-4084', 'EMP-2025-084', '2024-01-15', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1998-03-08', 'FEMALE', NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_sales, @g_emp, @t_mem,  @j_dist,'한지석', 'emp085@peoplecore.kr', '010-2085-4085', 'EMP-2025-085', '2024-06-22', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1999-07-16', 'MALE',   NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_sales, @g_emp, @t_mem,  @j_dist,'안유나', 'emp086@peoplecore.kr', '010-2086-4086', 'EMP-2025-086', '2024-11-08', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '2000-12-22', 'FEMALE', NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_sales, @g_emp, @t_mem,  @j_dist,'강민서', 'emp087@peoplecore.kr', '010-2087-4087', 'EMP-2025-087', '2025-04-30', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '2002-05-04', 'MALE',   NULL, NULL, 1, 100, 2, FALSE),
-- ───── 마케팅팀 (MKT) ─────
(@cid, @d_mkt,   @g_cha, @t_lead, @j_etc, '조지율', 'emp088@peoplecore.kr', '010-2088-4088', 'EMP-2025-088', '2018-04-22', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1981-09-18', 'FEMALE', NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_mkt,   @g_cha, @t_mem,  @j_etc, '홍연재', 'emp089@peoplecore.kr', '010-2089-4089', 'EMP-2025-089', '2019-11-12', 'CONTRACT', 'ACTIVE', @pwd, 'EMPLOYEE', '1983-12-12', 'MALE',   NULL, '2027-12-31', 1, 100, 2, FALSE),
(@cid, @d_mkt,   @g_cha, @t_mem,  @j_etc, '임도하', 'emp090@peoplecore.kr', '010-2090-4090', 'EMP-2025-090', '2020-08-04', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1984-08-25', 'MALE',   NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_mkt,   @g_gwa, @t_mem,  @j_etc, '신유나', 'emp091@peoplecore.kr', '010-2091-4091', 'EMP-2025-091', '2020-10-30', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1985-06-25', 'FEMALE', NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_mkt,   @g_gwa, @t_mem,  @j_etc, '양현서', 'emp092@peoplecore.kr', '010-2092-4092', 'EMP-2025-092', '2022-03-19', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1987-02-08', 'MALE',   NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_mkt,   @g_dae, @t_mem,  @j_etc, '백승현', 'emp093@peoplecore.kr', '010-2093-4093', 'EMP-2025-093', '2022-10-18', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1989-10-30', 'MALE',   NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_mkt,   @g_dae, @t_mem,  @j_etc, '서지유', 'emp094@peoplecore.kr', '010-2094-4094', 'EMP-2025-094', '2023-03-25', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1990-05-22', 'FEMALE', NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_mkt,   @g_dae, @t_mem,  @j_etc, '문도윤', 'emp095@peoplecore.kr', '010-2095-4095', 'EMP-2025-095', '2023-09-13', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1991-08-15', 'MALE',   NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_mkt,   @g_dae, @t_mem,  @j_etc, '노수민', 'emp096@peoplecore.kr', '010-2096-4096', 'EMP-2025-096', '2024-04-30', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1992-11-04', 'FEMALE', NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_mkt,   @g_emp, @t_mem,  @j_etc, '권채영', 'emp097@peoplecore.kr', '010-2097-4097', 'EMP-2025-097', '2023-11-15', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1996-04-26', 'FEMALE', NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_mkt,   @g_emp, @t_mem,  @j_etc, '정호준', 'emp098@peoplecore.kr', '010-2098-4098', 'EMP-2025-098', '2024-06-12', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '1998-07-15', 'MALE',   NULL, NULL, 1, 100, 2, FALSE),
(@cid, @d_mkt,   @g_emp, @t_mem,  @j_etc, '오수빈', 'emp099@peoplecore.kr', '010-2099-4099', 'EMP-2025-099', '2024-12-08', 'CONTRACT', 'ACTIVE', @pwd, 'EMPLOYEE', '1999-12-08', 'FEMALE', NULL, '2027-12-31', 1, 100, 2, FALSE),
(@cid, @d_mkt,   @g_emp, @t_mem,  @j_etc, '황민재', 'emp100@peoplecore.kr', '010-2100-4100', 'EMP-2025-100', '2025-05-15', 'FULL', 'ACTIVE',   @pwd, 'EMPLOYEE', '2001-06-30', 'MALE',   NULL, NULL, 1, 100, 2, FALSE);


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
