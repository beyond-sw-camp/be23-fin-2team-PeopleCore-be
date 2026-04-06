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


-- 사원 등록 임시 테스트용

● -- 부서: 중복 제거 (최초 1건만 남김)
DELETE FROM department
WHERE dept_name = '개발팀'
  AND dept_id != (SELECT min_id FROM (SELECT MIN(dept_id) AS min_id FROM department WHERE dept_name = '개발팀') tmp);

-- 직급: 중복 제거
DELETE FROM grade
WHERE grade_name = '대리'
  AND grade_id != (SELECT min_id FROM (SELECT MIN(grade_id) AS min_id FROM grade WHERE grade_name = '대리') tmp);

-- 직책: 중복 제거
DELETE FROM title
WHERE title_name = '파트장'
  AND title_id != (SELECT min_id FROM (SELECT MIN(title_id) AS min_id FROM title WHERE title_name = '파트장') tmp);

-- 계약 만료일 컬럼 추가 (인력현황 - 계약 만료 예정자 조회용)
ALTER TABLE employee ADD COLUMN contract_end_date DATE NULL;

-- -------------------------------------------
-- 회사 등록 시 default form양식
DELIMITER $$

CREATE TRIGGER trg_company_default_form_fields
    AFTER INSERT ON company
    FOR EACH ROW
BEGIN

    -- ══════════════════════════════════
    -- EMPLOYEE_REGISTER
    -- ══════════════════════════════════

    INSERT INTO form_field_setup
    (company_id, form_type, field_key, label, section, field_type, visible, required, sort_order, options, auto_fill_from)
    VALUES
        -- 기본 인적사항
        (NEW.id,'EMPLOYEE_REGISTER','empName',      '성명',           '기본 인적사항', 'TEXT',  1,1,1,NULL,NULL),
        (NEW.id,'EMPLOYEE_REGISTER','empNameEn',    '영문명',         '기본 인적사항', 'TEXT',  1,0,2,NULL,NULL),
        (NEW.id,'EMPLOYEE_REGISTER','birthDate',    '생년월일',       '기본 인적사항', 'DATE',  1,1,3,NULL,NULL),
        (NEW.id,'EMPLOYEE_REGISTER','gender',       '성별',           '기본 인적사항', 'RADIO', 1,1,4,NULL,NULL),
        (NEW.id,'EMPLOYEE_REGISTER','phone',        '연락처',         '기본 인적사항', 'TEXT',  1,1,5,NULL,NULL),
        (NEW.id,'EMPLOYEE_REGISTER','personalEmail','개인 이메일',    '기본 인적사항', 'TEXT',  1,1,6,NULL,NULL),
        (NEW.id,'EMPLOYEE_REGISTER','address',      '주소',           '기본 인적사항', 'TEXT',  1,0,7,NULL,NULL),
        -- 소속 및 고용 정보
        (NEW.id,'EMPLOYEE_REGISTER','hireDate',   '입사일',     '소속 및 고용 정보','DATE',  1,1,1,NULL,NULL),
        (NEW.id,'EMPLOYEE_REGISTER','employType', '고용 형태',  '소속 및 고용 정보','SELECT',1,1,2,'["정규직","계약직","시간제"]',NULL),
        (NEW.id,'EMPLOYEE_REGISTER','contractEnd','계약 만료일','소속 및 고용 정보','DATE',  1,0,3,NULL,NULL),
        (NEW.id,'EMPLOYEE_REGISTER','department', '부서',       '소속 및 고용 정보','SELECT',1,1,4,NULL,NULL),
        (NEW.id,'EMPLOYEE_REGISTER','rank',       '직급',       '소속 및 고용 정보','SELECT',1,1,5,NULL,NULL),
        (NEW.id,'EMPLOYEE_REGISTER','position',   '직책',       '소속 및 고용 정보','SELECT',1,1,6,NULL,NULL),
        -- 시스템 계정 설정
        (NEW.id,'EMPLOYEE_REGISTER','empId',        '사번',                  '시스템 계정 설정','AUTO',  1,1,1,NULL,NULL),
        (NEW.id,'EMPLOYEE_REGISTER','companyEmail', '사내 이메일',            '시스템 계정 설정','TEXT',  1,1,2,NULL,NULL),
        (NEW.id,'EMPLOYEE_REGISTER','pwMethod',     '초기 비밀번호 발급 방식','시스템 계정 설정','RADIO', 1,1,3,NULL,NULL),
        (NEW.id,'EMPLOYEE_REGISTER','mailQuota',    '메일함 용량',            '시스템 계정 설정','SELECT',1,0,4,'["5 GB (기본)","10 GB","20 GB","50 GB"]',NULL),
        -- 메뉴 / 기능 권한 설정
        (NEW.id,'EMPLOYEE_REGISTER','authTemplate','권한 템플릿','메뉴 / 기능 권한 설정','SELECT',1,1,1,'["일반 사원 (기본)","팀장","HR 담당자","재무 담당자","시스템 관리자"]',NULL),
        -- 인사 서류 등록
        (NEW.id,'EMPLOYEE_REGISTER','documents','서류 첨부','인사 서류 등록','FILE',1,0,1,NULL,NULL);

    -- ══════════════════════════════════
    -- SALARY_CONTRACT
    -- ══════════════════════════════════

    INSERT INTO form_field_setup
    (company_id, form_type, field_key, label, section, field_type, visible, required, sort_order, options, auto_fill_from)
    VALUES
        -- 인적사항
        (NEW.id,'SALARY_CONTRACT','empSearch', '사원 검색','인적사항','SEARCH',1,1,1,NULL,NULL),
        (NEW.id,'SALARY_CONTRACT','department','부서',     '인적사항','TEXT',  1,1,2,NULL,'department'),
        (NEW.id,'SALARY_CONTRACT','rank',      '직급',     '인적사항','TEXT',  1,1,3,NULL,'rank'),
        (NEW.id,'SALARY_CONTRACT','position',  '직책',     '인적사항','TEXT',  1,1,4,NULL,'position'),
        (NEW.id,'SALARY_CONTRACT','jobTitle',  '직무',     '인적사항','TEXT',  1,1,5,NULL,'jobTitle'),
        (NEW.id,'SALARY_CONTRACT','employType','근로형태', '인적사항','TEXT',  1,1,6,NULL,'employType'),
        -- 계약기간
        (NEW.id,'SALARY_CONTRACT','contractYear', '계약 연도',    '계약기간','SELECT',1,1,1,'["2026","2025","2024"]',NULL),
        (NEW.id,'SALARY_CONTRACT','contractStart','계약 시작일',  '계약기간','DATE',  1,1,2,NULL,NULL),
        (NEW.id,'SALARY_CONTRACT','contractEnd',  '계약 종료일',  '계약기간','DATE',  1,0,3,NULL,NULL),
        (NEW.id,'SALARY_CONTRACT','probation',    '수습 기간',    '계약기간','SELECT',1,0,4,'["없음","1개월","2개월","3개월"]',NULL),
        (NEW.id,'SALARY_CONTRACT','weeklyHours',  '주당 근로시간','계약기간','SELECT',1,1,5,'["40시간 (주 5일)","35시간","30시간","20시간 (시간제)","15시간 (단시간)"]',NULL),
        (NEW.id,'SALARY_CONTRACT','contractType', '계약서 유형',  '계약기간','SELECT',1,1,6,'["연봉계약서","근로계약서"]',NULL),
        -- 급여
        (NEW.id,'SALARY_CONTRACT','annualSalary','계약 연봉',    '급여','NUMBER',1,1,1,NULL,NULL),
        (NEW.id,'SALARY_CONTRACT','baseSalary',  '월 기본급',    '급여','NUMBER',1,1,2,NULL,NULL),
        (NEW.id,'SALARY_CONTRACT','extraSalary', '월 기본급 외', '급여','NUMBER',1,0,3,NULL,NULL),
        -- 기타사항
        (NEW.id,'SALARY_CONTRACT','memo',      '특약사항 / 메모',     '기타사항','TEXTAREA',1,0,1,NULL,NULL),
        (NEW.id,'SALARY_CONTRACT','attachment','서명 완료 계약서 첨부','기타사항','FILE',    1,0,2,NULL,NULL);

    -- ══════════════════════════════════
    -- HR_ORDER
    -- ══════════════════════════════════

    INSERT INTO form_field_setup
    (company_id, form_type, field_key, label, section, field_type, visible, required, sort_order, options, auto_fill_from)
    VALUES
        -- 발령 기본 정보
        (NEW.id,'HR_ORDER','orderDate',  '발령일자', '발령 기본 정보','DATE', 1,1,1,NULL,NULL),
        (NEW.id,'HR_ORDER','orderNumber','발령번호', '발령 기본 정보','AUTO', 1,1,2,NULL,NULL),
        (NEW.id,'HR_ORDER','orderTitle', '발령제목', '발령 기본 정보','TEXT', 1,1,3,NULL,NULL),
        (NEW.id,'HR_ORDER','orderCount', '발령 인원','발령 기본 정보','AUTO', 1,0,4,NULL,NULL),
        -- 발령 유형
        (NEW.id,'HR_ORDER','orderType','발령유형','발령 유형','SELECT',1,1,1,'["입사","퇴사","직위변경","부서변경","보직변경"]',NULL),
        -- 대상자 정보
        (NEW.id,'HR_ORDER','empSearch',    '사원명',   '대상자 정보','SEARCH',1,1,1,NULL,NULL),
        (NEW.id,'HR_ORDER','department',   '부서',     '대상자 정보','TEXT',  1,1,2,NULL,'department'),
        (NEW.id,'HR_ORDER','rank',         '직위',     '대상자 정보','TEXT',  1,1,3,NULL,'rank'),
        (NEW.id,'HR_ORDER','newDepartment','변경 부서','대상자 정보','SELECT',1,0,4,NULL,NULL),
        (NEW.id,'HR_ORDER','newRank',      '변경 직위','대상자 정보','SELECT',1,0,5,NULL,NULL),
        (NEW.id,'HR_ORDER','newPosition',  '변경 보직','대상자 정보','SELECT',1,0,6,NULL,NULL),
        (NEW.id,'HR_ORDER','empOrderDate', '발령일',   '대상자 정보','DATE',  1,1,7,NULL,NULL),
        -- 기타
        (NEW.id,'HR_ORDER','orderReason','발령 사유','기타','TEXTAREA',1,0,1,NULL,NULL);

    -- ══════════════════════════════════
    -- RESIGN_REGISTER
    -- ══════════════════════════════════

    INSERT INTO form_field_setup
    (company_id, form_type, field_key, label, section, field_type, visible, required, sort_order, options, auto_fill_from)
    VALUES
        -- 대상자
        (NEW.id,'RESIGN_REGISTER','empSearch', '사원 검색','대상자','SEARCH',1,1,1,NULL,NULL),
        (NEW.id,'RESIGN_REGISTER','department','부서',     '대상자','TEXT',  1,1,2,NULL,'department'),
        (NEW.id,'RESIGN_REGISTER','rank',      '직급',     '대상자','TEXT',  1,1,3,NULL,'rank'),
        (NEW.id,'RESIGN_REGISTER','hireDate',  '입사일',   '대상자','TEXT',  1,1,4,NULL,'hireDate'),
        -- 퇴직 정보
        (NEW.id,'RESIGN_REGISTER','resignDate',  '퇴직일',  '퇴직 정보','DATE',    1,1,1,NULL,NULL),
        (NEW.id,'RESIGN_REGISTER','resignType',  '퇴직 사유','퇴직 정보','SELECT',  1,1,2,'["자진퇴사","권고사직","정년퇴직","계약만료","기타"]',NULL),
        (NEW.id,'RESIGN_REGISTER','resignDetail','상세 사유','퇴직 정보','TEXTAREA',1,0,3,NULL,NULL),
        -- 인수인계 현황
        (NEW.id,'RESIGN_REGISTER','handoverWork',   '업무 인수인계 완료','인수인계 현황','RADIO',1,0,1,NULL,NULL),
        (NEW.id,'RESIGN_REGISTER','handoverEquip',  '장비 반납',        '인수인계 현황','RADIO',1,0,2,NULL,NULL),
        (NEW.id,'RESIGN_REGISTER','handoverAccount','계정 비활성화',    '인수인계 현황','RADIO',1,0,3,NULL,NULL),
        (NEW.id,'RESIGN_REGISTER','handoverPay',    '퇴직금 정산',      '인수인계 현황','RADIO',1,0,4,NULL,NULL);

    END$$

    DELIMITER ;

