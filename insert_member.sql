-- -- ============================================================
-- -- 가라회원 1명 데이터 적재 스크립트
-- -- 이메일: juwan7056@naver.com / 비밀번호: qwer1234
-- -- ※ 앱 실행 후 (ddl-auto: create로 테이블 생성된 뒤) 실행할 것
-- -- ============================================================
--
-- USE peoplecore;
--
-- -- 1. company 삽입
-- --    companyId는 UUID_TO_BIN으로 binary(16) 저장
-- INSERT INTO company (
--     company_id,
--     company_name,
--     founded_at,
--     company_ip,
--     contract_start_at,
--     contract_end_at,
--     contract_type,
--     max_employees,
--     company_status
-- ) VALUES (
--     UUID_TO_BIN('550e8400-e29b-41d4-a716-446655440000'),
--     '피플코어',
--     '2020-01-01',
--     NULL,
--     '2024-01-01',
--     '2025-12-31',
--     'YEARLY',
--     100,
--     'ACTIVE'
-- );
--
-- -- 2. grade 삽입 (직급)
-- INSERT INTO grade (
--     company_id,
--     grade_name,
--     grade_code,
--     grade_order
-- ) VALUES (
--     UUID_TO_BIN('550e8400-e29b-41d4-a716-446655440000'),
--     '사원',
--     'G001',
--     1
-- );
--
-- -- 3. title 삽입 (직위)
-- INSERT INTO title (
--     company_id,
--     dept_id,
--     title_name,
--     title_code
-- ) VALUES (
--     UUID_TO_BIN('550e8400-e29b-41d4-a716-446655440000'),
--     NULL,
--     '팀원',
--     'T001'
-- );
--
-- -- 4. department 삽입
-- INSERT INTO department (
--     company_id,
--     parent_dept_id,
--     dept_name,
--     dept_code,
--     created_at,
--     is_use
-- ) VALUES (
--     UUID_TO_BIN('550e8400-e29b-41d4-a716-446655440000'),
--     NULL,
--     '개발팀',
--     'DEV001',
--     NOW(),
--     'Y'
-- );
--
-- -- 5. employee 삽입
-- --    grade_id, title_id, dept_id는 위에서 자동 생성된 값 사용
-- INSERT INTO employee (
--     company_id,
--     dept_id,
--     grade_id,
--     title_id,
--     emp_name,
--     emp_email,
--     emp_phone,
--     emp_num,
--     emp_hire_date,
--     emp_type,
--     emp_status,
--     emp_password,
--     emp_role,
--     created_at,
--     updated_at
-- ) VALUES (
--     UUID_TO_BIN('550e8400-e29b-41d4-a716-446655440000'),
--     LAST_INSERT_ID(),   -- department 삽입 후 생성된 dept_id
--     (SELECT grade_id FROM grade WHERE grade_code = 'G001' AND company_id = UUID_TO_BIN('550e8400-e29b-41d4-a716-446655440000')),
--     (SELECT title_id FROM title WHERE title_code = 'T001' AND company_id = UUID_TO_BIN('550e8400-e29b-41d4-a716-446655440000')),
--     '주완',
--     'juwan7056@naver.com',
--     '010-0000-0000',
--     'EMP2024001',
--     '2024-01-01',
--     'FULL',
--     'ACTIVE',
--     '$2a$10$Rl7uSiWDhu9r/qj24D988edJIAikLsVY3glxQBugjk2nCrNTcj0A6',
--     'HR_ADMIN',
--     NOW(),
--     NOW()
-- );
--
-- -- 결과 확인
-- SELECT e.emp_id, e.emp_name, e.emp_email, e.emp_role, e.emp_status,
--        d.dept_name, g.grade_name, t.title_name
-- FROM employee e
-- JOIN department d ON e.dept_id = d.dept_id
-- JOIN grade g ON e.grade_id = g.grade_id
-- JOIN title t ON e.title_id = t.title_id
-- WHERE e.emp_email = 'juwan7056@naver.com';
--
--
--
-- ////////////////////
--


-- ============================================================
-- 가라회원 1명 데이터 적재 스크립트
-- 이메일: juwan7056@naver.com / 비밀번호: qwer1234
-- ※ 앱 실행 후 (ddl-auto: create로 테이블 생성된 뒤) 실행할 것
-- ============================================================

USE peoplecore;

-- 1. company 삽입
--    companyId는 UUID_TO_BIN으로 binary(16) 저장
INSERT INTO company (
    company_id,
    company_name,
    founded_at,
    company_ip,
    contract_start_at,
    contract_end_at,
    contract_type,
    max_employees,
    company_status
) VALUES (
             UUID_TO_BIN('550e8400-e29b-41d4-a716-446655440000'),
             '피플코어',
             '2020-01-01',
             NULL,
             '2024-01-01',
             '2025-12-31',
             'YEARLY',
             100,
             'ACTIVE'
         );

-- 2. grade 삽입 (직급)
INSERT INTO grade (
    company_id,
    grade_name,
    grade_code,
    grade_order
) VALUES (
             UUID_TO_BIN('550e8400-e29b-41d4-a716-446655440000'),
             '사원',
             'G001',
             1
         );

-- 3. title 삽입 (직위)
INSERT INTO title (
    company_id,
    dept_id,
    title_name,
    title_code
) VALUES (
             UUID_TO_BIN('550e8400-e29b-41d4-a716-446655440000'),
             NULL,
             '팀원',
             'T001'
         );

-- 4. department 삽입
INSERT INTO department (
    company_id,
    parent_dept_id,
    dept_name,
    dept_code,
    created_at,
    is_use
) VALUES (
             UUID_TO_BIN('550e8400-e29b-41d4-a716-446655440000'),
             NULL,
             '개발팀',
             'DEV001',
             NOW(),
             'Y'
         );

-- 5. employee 삽입
--    grade_id, title_id, dept_id는 위에서 자동 생성된 값 사용
INSERT INTO employee (
    company_id,
    dept_id,
    grade_id,
    title_id,
    emp_name,
    emp_email,
    emp_phone,
    emp_num,
    emp_hire_date,
    emp_type,
    emp_status,
    emp_password,
    emp_role,
    created_at,
    updated_at
) VALUES (
             UUID_TO_BIN('550e8400-e29b-41d4-a716-446655440000'),
             LAST_INSERT_ID(),   -- department 삽입 후 생성된 dept_id
             (SELECT grade_id FROM grade WHERE grade_code = 'G001' AND company_id = UUID_TO_BIN('550e8400-e29b-41d4-a716-446655440000')),
             (SELECT title_id FROM title WHERE title_code = 'T001' AND company_id = UUID_TO_BIN('550e8400-e29b-41d4-a716-446655440000')),
             '주완',
             'juwan7056@naver.com',
             '010-0000-0000',
             'EMP2024001',
             '2024-01-01',
             'FULL',
             'ACTIVE',
             '$2a$10$Rl7uSiWDhu9r/qj24D988edJIAikLsVY3glxQBugjk2nCrNTcj0A6',
             'EMPLOYEE',
             NOW(),
             NOW()
         );
