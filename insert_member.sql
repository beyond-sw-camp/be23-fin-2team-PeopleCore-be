-- ============================================================
-- 가라회원 1명 데이터 적재 스크립트
-- 이메일: juwan7056@naver.com / 비밀번호: qwer1234
-- company_id: 550e8400-e29b-41d4-a716-446655440000
-- Spring Boot 3.5 / Hibernate 6 → UUID는 BINARY(16) 저장
-- ============================================================

USE peoplecore;

-- 1. company 없으면 삽입
INSERT IGNORE INTO company (company_id)
VALUES (UUID_TO_BIN('550e8400-e29b-41d4-a716-446655440000'));

-- 2. grade 없으면 삽입 (id=1 확보)
INSERT IGNORE INTO grade (grade_id) VALUES (1);

-- 3. title 없으면 삽입 (id=1 확보)
INSERT IGNORE INTO title (title_id) VALUES (1);

-- 4. 부서 없으면 삽입 (id=1 확보)
INSERT IGNORE INTO `부서` (id, company_id, dept_name, dept_code, created_at, is_use)
VALUES (
    1,
    UUID_TO_BIN('550e8400-e29b-41d4-a716-446655440000'),
    '개발팀',
    'DEV001',
    NOW(),
    'Y'
);

-- 5. 사원 삽입 (중복 방지)
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
)
SELECT
    UUID_TO_BIN('550e8400-e29b-41d4-a716-446655440000'),
    1,
    1,
    1,
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
WHERE NOT EXISTS (
    SELECT 1 FROM employee
    WHERE company_id = UUID_TO_BIN('550e8400-e29b-41d4-a716-446655440000')
      AND emp_email = 'juwan7056@naver.com'
);

-- 결과 확인
SELECT emp_id, emp_name, emp_email, emp_role, emp_status
FROM employee
WHERE emp_email = 'juwan7056@naver.com';
