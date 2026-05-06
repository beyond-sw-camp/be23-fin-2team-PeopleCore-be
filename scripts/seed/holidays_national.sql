-- =====================================================================
-- 법정공휴일(NATIONAL) 시드 데이터
-- ---------------------------------------------------------------------
-- 대상 DB : MySQL 8.x
-- companyId 는 NULL (NATIONAL 은 전 회사 공유)
-- empId    는 시스템 적재자 (HR_SUPER_ADMIN 첫 사원)
-- isRepeating=false 로 일자별 명시 적재 (대체공휴일 포함 위해)
--
-- ⚠️ 정확성 주의:
--   - 2024, 2025 는 확정 데이터
--   - 2026 은 본 스펙 작성 시점(2026-05-05) 기준 상반기 확정 + 하반기는 인사혁신처 공시 검증 필수
--   - 2027 은 추정. 인사혁신처 익년 공시 후 본 SQL 갱신 필요
-- =====================================================================
USE peoplecore;

SET @sys_emp_id = (SELECT emp_id FROM employee
                    WHERE emp_role = 'HR_SUPER_ADMIN' ORDER BY emp_id LIMIT 1);

-- ===== 2024 =====
INSERT INTO holidays (date, holiday_name, holiday_type, is_repeating, company_id, emp_id, created_at, updated_at) VALUES
                                                                                                                      ('2024-01-01', '신정',           'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2024-02-09', '설날 연휴',      'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2024-02-10', '설날',           'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2024-02-11', '설날 연휴',      'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2024-02-12', '설날 대체공휴일', 'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2024-03-01', '삼일절',         'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2024-04-10', '제22대 국회의원선거','NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2024-05-05', '어린이날',       'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2024-05-06', '어린이날 대체공휴일','NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2024-05-15', '부처님오신날',   'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2024-06-06', '현충일',         'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2024-08-15', '광복절',         'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2024-09-16', '추석 연휴',      'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2024-09-17', '추석',           'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2024-09-18', '추석 연휴',      'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2024-10-01', '국군의 날',      'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2024-10-03', '개천절',         'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2024-10-09', '한글날',         'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2024-12-25', '성탄절',         'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW());

-- ===== 2025 =====
INSERT INTO holidays (date, holiday_name, holiday_type, is_repeating, company_id, emp_id, created_at, updated_at) VALUES
                                                                                                                      ('2025-01-01', '신정',           'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2025-01-27', '임시공휴일',     'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2025-01-28', '설날 연휴',      'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2025-01-29', '설날',           'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2025-01-30', '설날 연휴',      'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2025-03-01', '삼일절',         'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2025-03-03', '삼일절 대체공휴일','NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2025-05-05', '어린이날',       'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2025-05-05', '부처님오신날',   'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2025-05-06', '대체공휴일',     'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2025-06-06', '현충일',         'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2025-08-15', '광복절',         'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2025-10-03', '개천절',         'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2025-10-05', '추석 연휴',      'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2025-10-06', '추석',           'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2025-10-07', '추석 연휴',      'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2025-10-08', '추석 대체공휴일', 'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2025-10-09', '한글날',         'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2025-12-25', '성탄절',         'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW());

-- ===== 2026 =====  (※ 하반기 대체공휴일은 인사혁신처 공시 검증 필수)
INSERT INTO holidays (date, holiday_name, holiday_type, is_repeating, company_id, emp_id, created_at, updated_at) VALUES
                                                                                                                      ('2026-01-01', '신정',           'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2026-02-16', '설날 연휴',      'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2026-02-17', '설날',           'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2026-02-18', '설날 연휴',      'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2026-03-01', '삼일절',         'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2026-03-02', '삼일절 대체공휴일','NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2026-05-05', '어린이날',       'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2026-05-24', '부처님오신날',   'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2026-05-25', '부처님오신날 대체공휴일','NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2026-06-06', '현충일',         'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2026-08-15', '광복절',         'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2026-08-17', '광복절 대체공휴일','NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2026-09-24', '추석 연휴',      'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2026-09-25', '추석',           'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2026-09-26', '추석 연휴',      'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2026-10-03', '개천절',         'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2026-10-05', '개천절 대체공휴일','NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2026-10-09', '한글날',         'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2026-12-25', '성탄절',         'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW());

-- ===== 2027 =====  (※ 추정. 정부 공시 후 본 SQL 갱신 필요)
INSERT INTO holidays (date, holiday_name, holiday_type, is_repeating, company_id, emp_id, created_at, updated_at) VALUES
                                                                                                                      ('2027-01-01', '신정',           'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2027-02-06', '설날 연휴',      'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2027-02-07', '설날',           'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2027-02-08', '설날 연휴',      'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2027-02-09', '설날 대체공휴일', 'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2027-03-01', '삼일절',         'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2027-05-05', '어린이날',       'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2027-05-13', '부처님오신날',   'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2027-06-06', '현충일',         'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2027-08-15', '광복절',         'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2027-08-16', '광복절 대체공휴일','NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2027-09-14', '추석 연휴',      'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2027-09-15', '추석',           'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2027-09-16', '추석 연휴',      'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2027-10-03', '개천절',         'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2027-10-04', '개천절 대체공휴일','NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2027-10-09', '한글날',         'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2027-10-11', '한글날 대체공휴일','NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2027-12-25', '성탄절',         'NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW()),
                                                                                                                      ('2027-12-27', '성탄절 대체공휴일','NATIONAL', false, NULL, @sys_emp_id, NOW(), NOW());

-- 검증
SELECT YEAR(date) AS yr, COUNT(*) FROM holidays
WHERE holiday_type = 'NATIONAL' GROUP BY YEAR(date) ORDER BY yr;