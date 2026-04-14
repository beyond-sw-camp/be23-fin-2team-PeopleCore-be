/* =========================================================================
   HR 근태 현황 테스트용 시드 데이터
   - 대상 API:
       GET /attendance/admin/daily/period/list
       GET /attendance/admin/daily/weekly-stats
       GET /attendance/admin/daily/dept-summary
       GET /attendance/admin/daily/overtime
   - 기간: 2026-04-13(월) ~ 2026-04-19(일)
   - 구성: 1회사 / 5부서 / 5직급 / 1근무그룹 / 1초과근무정책 / 12사원
   - 실행 전 확인사항:
       (1) company, department, grade, work_group 테이블이 비어있거나
           아래 하드코딩 ID 와 충돌 없는지 확인
       (2) employee 테이블의 NOT NULL 컬럼이 DB 스키마마다 다를 수 있음 —
           실행 시 오류 나면 누락 컬럼 추가
       (3) 회사 UUID 는 아래 @COMPANY_ID 변수로 공통 사용
       (4) commute_record / attendance 는 RANGE 파티션이므로 2026-04 파티션이
           존재해야 함. 없으면 먼저 ALTER TABLE … ADD PARTITION 선행 필요
   ========================================================================= */

/* 공통 변수 ---------------------------------------------------------------- */
SET @COMPANY_ID   = UUID_TO_BIN('11111111-1111-1111-1111-111111111111');
SET @WORKGROUP_ID = 1;
SET @POLICY_ID    = 1;

/* 1. 회사 ------------------------------------------------------------------ */
INSERT INTO company (company_id, company_name)
VALUES (@COMPANY_ID, 'PeopleCore 테스트회사')
ON DUPLICATE KEY UPDATE company_name = VALUES(company_name);

/* 2. 부서 (5개) ------------------------------------------------------------ */
INSERT INTO department (dept_id, company_id, dept_name) VALUES
  (1, @COMPANY_ID, '개발팀'),
  (2, @COMPANY_ID, '인사팀'),
  (3, @COMPANY_ID, '마케팅팀'),
  (4, @COMPANY_ID, '영업팀'),
  (5, @COMPANY_ID, '기획팀')
ON DUPLICATE KEY UPDATE dept_name = VALUES(dept_name);

/* 3. 직급 (5개) ------------------------------------------------------------ */
INSERT INTO grade (grade_id, company_id, grade_name) VALUES
  (1, @COMPANY_ID, '사원'),
  (2, @COMPANY_ID, '대리'),
  (3, @COMPANY_ID, '과장'),
  (4, @COMPANY_ID, '차장'),
  (5, @COMPANY_ID, '부장')
ON DUPLICATE KEY UPDATE grade_name = VALUES(grade_name);

/* 4. 근무그룹 — 09:00-18:00, 월~금, 12:00-13:00 브레이크 ------------------ */
INSERT INTO work_group
  (work_group_id, company_id, group_name, group_code, group_desc,
   group_start_time, group_end_time, group_work_day,
   group_break_start, group_break_end,
   group_overtime_recognize, group_mobile_check,
   group_manager_id, group_manager_name,
   created_at, updated_at)
VALUES
  (@WORKGROUP_ID, @COMPANY_ID, '표준근무', 'STD', '월~금 09-18 표준 근무그룹',
   '09:00:00', '18:00:00', 31,               /* 비트마스크: 월1+화2+수4+목8+금16 = 31 */
   '12:00:00', '13:00:00',
   'APPROVAL', FALSE,
   NULL, NULL, NOW(), NOW())
ON DUPLICATE KEY UPDATE group_name = VALUES(group_name);

/* 5. 초과근무 정책 — 주 최대 52h / 경고 48h -------------------------------- */
INSERT INTO overtime_policy
  (ot_policy_id, company_id, ot_min_unit,
   ot_policy_weekly_max_hour, ot_policy_warning_hour, ot_exceed_action,
   ot_policy_manager_id, ot_policy_manager_name)
VALUES
  (@POLICY_ID, @COMPANY_ID, 'FIFTEEN', 52, 48, 'NOTIFY', NULL, NULL)
ON DUPLICATE KEY UPDATE
  ot_policy_weekly_max_hour = VALUES(ot_policy_weekly_max_hour),
  ot_policy_warning_hour    = VALUES(ot_policy_warning_hour);

/* 6. 사원 (12명) ----------------------------------------------------------- */
/* 실제 스키마에 맞춰 NOT NULL 컬럼(emp_phone, contract_end_at 등) 포함                */
/* contract_end_at: 정규직은 계약만료 개념이 약해서 2099-12-31 23:59:59 로 세팅         */
INSERT INTO employee
  (emp_id, company_id, dept_id, grade_id, work_group_id, work_group_assigned_at,
   emp_num, emp_name, emp_email, emp_phone, emp_hire_date,
   emp_type, emp_status, emp_role,
   emp_password, must_change_password, dependents_count, tax_rate_option,
   contract_end_at)
VALUES
  (1,  @COMPANY_ID, 1, 3, @WORKGROUP_ID, NOW(), 'EMP001', '김민수', 'emp001@test.co', '010-1001-0001', '2020-03-01', 'REGULAR', 'ACTIVE', 'EMPLOYEE',     '', FALSE, 1, 100, '2099-12-31 23:59:59'),
  (2,  @COMPANY_ID, 1, 2, @WORKGROUP_ID, NOW(), 'EMP002', '이영희', 'emp002@test.co', '010-1001-0002', '2021-05-01', 'REGULAR', 'ACTIVE', 'EMPLOYEE',     '', FALSE, 1, 100, '2099-12-31 23:59:59'),
  (3,  @COMPANY_ID, 1, 1, @WORKGROUP_ID, NOW(), 'EMP003', '박지훈', 'emp003@test.co', '010-1001-0003', '2023-01-02', 'REGULAR', 'ACTIVE', 'EMPLOYEE',     '', FALSE, 1, 100, '2099-12-31 23:59:59'),
  (4,  @COMPANY_ID, 1, 1, @WORKGROUP_ID, NOW(), 'EMP004', '최서연', 'emp004@test.co', '010-1001-0004', '2024-06-15', 'REGULAR', 'ACTIVE', 'EMPLOYEE',     '', FALSE, 1, 100, '2099-12-31 23:59:59'),
  (5,  @COMPANY_ID, 2, 4, @WORKGROUP_ID, NOW(), 'EMP005', '정하늘', 'emp005@test.co', '010-1001-0005', '2018-09-10', 'REGULAR', 'ACTIVE', 'HR_ADMIN',     '', FALSE, 2, 100, '2099-12-31 23:59:59'),
  (6,  @COMPANY_ID, 3, 4, @WORKGROUP_ID, NOW(), 'EMP006', '강도윤', 'emp006@test.co', '010-1001-0006', '2019-02-01', 'REGULAR', 'ACTIVE', 'EMPLOYEE',     '', FALSE, 1, 100, '2099-12-31 23:59:59'),
  (7,  @COMPANY_ID, 3, 2, @WORKGROUP_ID, NOW(), 'EMP007', '윤지아', 'emp007@test.co', '010-1001-0007', '2022-04-20', 'REGULAR', 'ACTIVE', 'EMPLOYEE',     '', FALSE, 1, 100, '2099-12-31 23:59:59'),
  (8,  @COMPANY_ID, 4, 5, @WORKGROUP_ID, NOW(), 'EMP008', '임재호', 'emp008@test.co', '010-1001-0008', '2015-07-01', 'REGULAR', 'ACTIVE', 'EMPLOYEE',     '', FALSE, 3, 100, '2099-12-31 23:59:59'),
  (9,  @COMPANY_ID, 4, 2, @WORKGROUP_ID, NOW(), 'EMP009', '한소영', 'emp009@test.co', '010-1001-0009', '2022-11-05', 'REGULAR', 'ACTIVE', 'EMPLOYEE',     '', FALSE, 1, 100, '2099-12-31 23:59:59'),
  (10, @COMPANY_ID, 5, 3, @WORKGROUP_ID, NOW(), 'EMP010', '오준혁', 'emp010@test.co', '010-1001-0010', '2020-08-15', 'REGULAR', 'ACTIVE', 'EMPLOYEE',     '', FALSE, 1, 100, '2099-12-31 23:59:59'),
  (11, @COMPANY_ID, 2, 2, @WORKGROUP_ID, NOW(), 'EMP011', '김지원', 'emp011@test.co', '010-1001-0011', '2023-03-02', 'REGULAR', 'ACTIVE', 'EMPLOYEE',     '', FALSE, 1, 100, '2099-12-31 23:59:59'),
  (12, @COMPANY_ID, 5, 2, @WORKGROUP_ID, NOW(), 'EMP012', '이태민', 'emp012@test.co', '010-1001-0012', '2021-09-01', 'REGULAR', 'ACTIVE', 'EMPLOYEE',     '', FALSE, 1, 100, '2099-12-31 23:59:59')
ON DUPLICATE KEY UPDATE emp_name = VALUES(emp_name);

/* 7. 출퇴근 기록 (commute_record) ------------------------------------------ */
/* 기간: 2026-04-13(월) ~ 2026-04-19(일)
   분포 의도:
     - 대부분 정상출근
     - EMP003: 월~금 늦게까지 근무 (UNAPPROVED_OT 반복) + 토요일 승인 OT 출근
     - EMP001/008/006/010: 일부 일자 초과근무
     - EMP007: 화요일 지각, 목요일 조퇴
     - EMP004: 금요일 결근
     - EMP005: 월요일 승인 휴가 (연차)                                         */

/* 공통: ON_TIME/정상 체크인 체크아웃 매크로 스타일 — 일자만 바꿔서 반복 ---- */

INSERT INTO commute_record
  (work_date, company_id, emp_id,
   com_rec_check_in, com_rec_check_out, check_in_ip, check_out_ip,
   is_offsite, check_in_status, check_out_status, holiday_reason,
   actual_work_minutes, overtime_minutes,
   recognized_extended_minutes, recognized_night_minutes, recognized_holiday_minutes,
   created_at, updated_at)
VALUES
/* ===== 2026-04-13 (월) ===== */
  ('2026-04-13', @COMPANY_ID, 1, '2026-04-13 08:55:00','2026-04-13 19:30:00','10.0.0.1','10.0.0.1',FALSE,'ON_TIME','ON_TIME',NULL,540, 90,0,0,0,NOW(),NOW()),
  ('2026-04-13', @COMPANY_ID, 2, '2026-04-13 08:58:00','2026-04-13 18:05:00','10.0.0.2','10.0.0.2',FALSE,'ON_TIME','ON_TIME',NULL,540,  0,0,0,0,NOW(),NOW()),
  ('2026-04-13', @COMPANY_ID, 3, '2026-04-13 08:50:00','2026-04-13 22:00:00','10.0.0.3','10.0.0.3',FALSE,'ON_TIME','ON_TIME',NULL,540,240,0,0,0,NOW(),NOW()),
  ('2026-04-13', @COMPANY_ID, 4, '2026-04-13 09:00:00','2026-04-13 18:00:00','10.0.0.4','10.0.0.4',FALSE,'ON_TIME','ON_TIME',NULL,540,  0,0,0,0,NOW(),NOW()),
  /* EMP005: 당일 승인 휴가 — 출근 기록 없음 */
  ('2026-04-13', @COMPANY_ID, 6, '2026-04-13 08:45:00','2026-04-13 20:00:00','10.0.0.6','10.0.0.6',FALSE,'ON_TIME','ON_TIME',NULL,540,120,0,0,0,NOW(),NOW()),
  ('2026-04-13', @COMPANY_ID, 7, '2026-04-13 09:00:00','2026-04-13 18:10:00','10.0.0.7','10.0.0.7',FALSE,'ON_TIME','ON_TIME',NULL,540,  0,0,0,0,NOW(),NOW()),
  ('2026-04-13', @COMPANY_ID, 8, '2026-04-13 08:40:00','2026-04-13 19:00:00','10.0.0.8','10.0.0.8',FALSE,'ON_TIME','ON_TIME',NULL,540, 60,0,0,0,NOW(),NOW()),
  ('2026-04-13', @COMPANY_ID, 9, '2026-04-13 08:55:00','2026-04-13 18:00:00','10.0.0.9','10.0.0.9',FALSE,'ON_TIME','ON_TIME',NULL,540,  0,0,0,0,NOW(),NOW()),
  ('2026-04-13', @COMPANY_ID,10, '2026-04-13 09:00:00','2026-04-13 18:30:00','10.0.0.10','10.0.0.10',FALSE,'ON_TIME','ON_TIME',NULL,540, 30,0,0,0,NOW(),NOW()),
  ('2026-04-13', @COMPANY_ID,11, '2026-04-13 09:00:00','2026-04-13 18:00:00','10.0.0.11','10.0.0.11',FALSE,'ON_TIME','ON_TIME',NULL,540,  0,0,0,0,NOW(),NOW()),
  ('2026-04-13', @COMPANY_ID,12, '2026-04-13 09:05:00','2026-04-13 18:10:00','10.0.0.12','10.0.0.12',FALSE,'ON_TIME','ON_TIME',NULL,540,  0,0,0,0,NOW(),NOW()),

/* ===== 2026-04-14 (화) ===== */
  ('2026-04-14', @COMPANY_ID, 1, '2026-04-14 08:50:00','2026-04-14 19:00:00','10.0.0.1','10.0.0.1',FALSE,'ON_TIME','ON_TIME',NULL,540, 60,0,0,0,NOW(),NOW()),
  ('2026-04-14', @COMPANY_ID, 2, '2026-04-14 09:00:00','2026-04-14 18:00:00','10.0.0.2','10.0.0.2',FALSE,'ON_TIME','ON_TIME',NULL,540,  0,0,0,0,NOW(),NOW()),
  ('2026-04-14', @COMPANY_ID, 3, '2026-04-14 08:55:00','2026-04-14 22:30:00','10.0.0.3','10.0.0.3',FALSE,'ON_TIME','ON_TIME',NULL,540,270,0, 30,0,NOW(),NOW()),
  ('2026-04-14', @COMPANY_ID, 4, '2026-04-14 09:00:00','2026-04-14 18:00:00','10.0.0.4','10.0.0.4',FALSE,'ON_TIME','ON_TIME',NULL,540,  0,0,0,0,NOW(),NOW()),
  ('2026-04-14', @COMPANY_ID, 5, '2026-04-14 09:00:00','2026-04-14 18:00:00','10.0.0.5','10.0.0.5',FALSE,'ON_TIME','ON_TIME',NULL,540,  0,0,0,0,NOW(),NOW()),
  ('2026-04-14', @COMPANY_ID, 6, '2026-04-14 08:45:00','2026-04-14 19:30:00','10.0.0.6','10.0.0.6',FALSE,'ON_TIME','ON_TIME',NULL,540, 90,0,0,0,NOW(),NOW()),
  /* EMP007 지각 (09:20 출근) */
  ('2026-04-14', @COMPANY_ID, 7, '2026-04-14 09:20:00','2026-04-14 18:00:00','10.0.0.7','10.0.0.7',FALSE,'LATE','ON_TIME',NULL,520,  0,0,0,0,NOW(),NOW()),
  ('2026-04-14', @COMPANY_ID, 8, '2026-04-14 08:50:00','2026-04-14 20:00:00','10.0.0.8','10.0.0.8',FALSE,'ON_TIME','ON_TIME',NULL,540,120,0,0,0,NOW(),NOW()),
  ('2026-04-14', @COMPANY_ID, 9, '2026-04-14 09:00:00','2026-04-14 18:00:00','10.0.0.9','10.0.0.9',FALSE,'ON_TIME','ON_TIME',NULL,540,  0,0,0,0,NOW(),NOW()),
  ('2026-04-14', @COMPANY_ID,10, '2026-04-14 08:55:00','2026-04-14 19:00:00','10.0.0.10','10.0.0.10',FALSE,'ON_TIME','ON_TIME',NULL,540, 60,0,0,0,NOW(),NOW()),
  /* EMP011 지각 (09:15 출근) */
  ('2026-04-14', @COMPANY_ID,11, '2026-04-14 09:15:00','2026-04-14 18:00:00','10.0.0.11','10.0.0.11',FALSE,'LATE','ON_TIME',NULL,525,  0,0,0,0,NOW(),NOW()),
  ('2026-04-14', @COMPANY_ID,12, '2026-04-14 09:00:00','2026-04-14 18:00:00','10.0.0.12','10.0.0.12',FALSE,'ON_TIME','ON_TIME',NULL,540,  0,0,0,0,NOW(),NOW()),

/* ===== 2026-04-15 (수) — 전원 정상 + 일부 초과근무 ===== */
  ('2026-04-15', @COMPANY_ID, 1, '2026-04-15 08:55:00','2026-04-15 19:00:00','10.0.0.1','10.0.0.1',FALSE,'ON_TIME','ON_TIME',NULL,540, 60,0,0,0,NOW(),NOW()),
  ('2026-04-15', @COMPANY_ID, 2, '2026-04-15 09:00:00','2026-04-15 18:00:00','10.0.0.2','10.0.0.2',FALSE,'ON_TIME','ON_TIME',NULL,540,  0,0,0,0,NOW(),NOW()),
  ('2026-04-15', @COMPANY_ID, 3, '2026-04-15 08:55:00','2026-04-15 21:30:00','10.0.0.3','10.0.0.3',FALSE,'ON_TIME','ON_TIME',NULL,540,210,0,0,0,NOW(),NOW()),
  ('2026-04-15', @COMPANY_ID, 4, '2026-04-15 09:00:00','2026-04-15 18:00:00','10.0.0.4','10.0.0.4',FALSE,'ON_TIME','ON_TIME',NULL,540,  0,0,0,0,NOW(),NOW()),
  ('2026-04-15', @COMPANY_ID, 5, '2026-04-15 09:00:00','2026-04-15 18:00:00','10.0.0.5','10.0.0.5',FALSE,'ON_TIME','ON_TIME',NULL,540,  0,0,0,0,NOW(),NOW()),
  ('2026-04-15', @COMPANY_ID, 6, '2026-04-15 08:50:00','2026-04-15 18:10:00','10.0.0.6','10.0.0.6',FALSE,'ON_TIME','ON_TIME',NULL,540,  0,0,0,0,NOW(),NOW()),
  ('2026-04-15', @COMPANY_ID, 7, '2026-04-15 09:00:00','2026-04-15 18:00:00','10.0.0.7','10.0.0.7',FALSE,'ON_TIME','ON_TIME',NULL,540,  0,0,0,0,NOW(),NOW()),
  ('2026-04-15', @COMPANY_ID, 8, '2026-04-15 08:45:00','2026-04-15 19:00:00','10.0.0.8','10.0.0.8',FALSE,'ON_TIME','ON_TIME',NULL,540, 60,0,0,0,NOW(),NOW()),
  ('2026-04-15', @COMPANY_ID, 9, '2026-04-15 09:00:00','2026-04-15 18:00:00','10.0.0.9','10.0.0.9',FALSE,'ON_TIME','ON_TIME',NULL,540,  0,0,0,0,NOW(),NOW()),
  ('2026-04-15', @COMPANY_ID,10, '2026-04-15 09:00:00','2026-04-15 18:30:00','10.0.0.10','10.0.0.10',FALSE,'ON_TIME','ON_TIME',NULL,540, 30,0,0,0,NOW(),NOW()),
  ('2026-04-15', @COMPANY_ID,11, '2026-04-15 09:00:00','2026-04-15 18:00:00','10.0.0.11','10.0.0.11',FALSE,'ON_TIME','ON_TIME',NULL,540,  0,0,0,0,NOW(),NOW()),
  ('2026-04-15', @COMPANY_ID,12, '2026-04-15 08:58:00','2026-04-15 18:00:00','10.0.0.12','10.0.0.12',FALSE,'ON_TIME','ON_TIME',NULL,540,  0,0,0,0,NOW(),NOW()),

/* ===== 2026-04-16 (목) — EMP007 조퇴, EMP001 지각 ===== */
  ('2026-04-16', @COMPANY_ID, 1, '2026-04-16 09:10:00','2026-04-16 19:00:00','10.0.0.1','10.0.0.1',FALSE,'LATE','ON_TIME',NULL,530, 60,0,0,0,NOW(),NOW()),
  ('2026-04-16', @COMPANY_ID, 2, '2026-04-16 09:00:00','2026-04-16 18:00:00','10.0.0.2','10.0.0.2',FALSE,'ON_TIME','ON_TIME',NULL,540,  0,0,0,0,NOW(),NOW()),
  ('2026-04-16', @COMPANY_ID, 3, '2026-04-16 08:55:00','2026-04-16 21:00:00','10.0.0.3','10.0.0.3',FALSE,'ON_TIME','ON_TIME',NULL,540,180,0,0,0,NOW(),NOW()),
  ('2026-04-16', @COMPANY_ID, 4, '2026-04-16 09:00:00','2026-04-16 18:00:00','10.0.0.4','10.0.0.4',FALSE,'ON_TIME','ON_TIME',NULL,540,  0,0,0,0,NOW(),NOW()),
  ('2026-04-16', @COMPANY_ID, 5, '2026-04-16 09:00:00','2026-04-16 18:00:00','10.0.0.5','10.0.0.5',FALSE,'ON_TIME','ON_TIME',NULL,540,  0,0,0,0,NOW(),NOW()),
  ('2026-04-16', @COMPANY_ID, 6, '2026-04-16 08:50:00','2026-04-16 18:00:00','10.0.0.6','10.0.0.6',FALSE,'ON_TIME','ON_TIME',NULL,540,  0,0,0,0,NOW(),NOW()),
  /* EMP007 조퇴 (17:00 퇴근) */
  ('2026-04-16', @COMPANY_ID, 7, '2026-04-16 09:00:00','2026-04-16 17:00:00','10.0.0.7','10.0.0.7',FALSE,'ON_TIME','EARLY_LEAVE',NULL,480,  0,0,0,0,NOW(),NOW()),
  ('2026-04-16', @COMPANY_ID, 8, '2026-04-16 08:45:00','2026-04-16 19:30:00','10.0.0.8','10.0.0.8',FALSE,'ON_TIME','ON_TIME',NULL,540, 90,0,0,0,NOW(),NOW()),
  ('2026-04-16', @COMPANY_ID, 9, '2026-04-16 09:00:00','2026-04-16 18:00:00','10.0.0.9','10.0.0.9',FALSE,'ON_TIME','ON_TIME',NULL,540,  0,0,0,0,NOW(),NOW()),
  ('2026-04-16', @COMPANY_ID,10, '2026-04-16 09:00:00','2026-04-16 18:30:00','10.0.0.10','10.0.0.10',FALSE,'ON_TIME','ON_TIME',NULL,540, 30,0,0,0,NOW(),NOW()),
  ('2026-04-16', @COMPANY_ID,11, '2026-04-16 09:00:00','2026-04-16 18:00:00','10.0.0.11','10.0.0.11',FALSE,'ON_TIME','ON_TIME',NULL,540,  0,0,0,0,NOW(),NOW()),
  ('2026-04-16', @COMPANY_ID,12, '2026-04-16 09:00:00','2026-04-16 18:00:00','10.0.0.12','10.0.0.12',FALSE,'ON_TIME','ON_TIME',NULL,540,  0,0,0,0,NOW(),NOW()),

/* ===== 2026-04-17 (금) — EMP004 결근, EMP002 지각, EMP009 조퇴 ===== */
  ('2026-04-17', @COMPANY_ID, 1, '2026-04-17 08:50:00','2026-04-17 19:30:00','10.0.0.1','10.0.0.1',FALSE,'ON_TIME','ON_TIME',NULL,540, 90,0,0,0,NOW(),NOW()),
  /* EMP002 지각 */
  ('2026-04-17', @COMPANY_ID, 2, '2026-04-17 09:25:00','2026-04-17 18:00:00','10.0.0.2','10.0.0.2',FALSE,'LATE','ON_TIME',NULL,515,  0,0,0,0,NOW(),NOW()),
  ('2026-04-17', @COMPANY_ID, 3, '2026-04-17 08:50:00','2026-04-17 23:00:00','10.0.0.3','10.0.0.3',FALSE,'ON_TIME','ON_TIME',NULL,540,300,0, 60,0,NOW(),NOW()),
  /* EMP004 결근 — 레코드 없음 */
  ('2026-04-17', @COMPANY_ID, 5, '2026-04-17 09:00:00','2026-04-17 18:00:00','10.0.0.5','10.0.0.5',FALSE,'ON_TIME','ON_TIME',NULL,540,  0,0,0,0,NOW(),NOW()),
  ('2026-04-17', @COMPANY_ID, 6, '2026-04-17 08:45:00','2026-04-17 19:00:00','10.0.0.6','10.0.0.6',FALSE,'ON_TIME','ON_TIME',NULL,540, 60,0,0,0,NOW(),NOW()),
  ('2026-04-17', @COMPANY_ID, 7, '2026-04-17 09:00:00','2026-04-17 18:10:00','10.0.0.7','10.0.0.7',FALSE,'ON_TIME','ON_TIME',NULL,540,  0,0,0,0,NOW(),NOW()),
  ('2026-04-17', @COMPANY_ID, 8, '2026-04-17 08:40:00','2026-04-17 20:00:00','10.0.0.8','10.0.0.8',FALSE,'ON_TIME','ON_TIME',NULL,540,120,0,0,0,NOW(),NOW()),
  /* EMP009 조퇴 (16:30 퇴근) */
  ('2026-04-17', @COMPANY_ID, 9, '2026-04-17 09:00:00','2026-04-17 16:30:00','10.0.0.9','10.0.0.9',FALSE,'ON_TIME','EARLY_LEAVE',NULL,450,  0,0,0,0,NOW(),NOW()),
  ('2026-04-17', @COMPANY_ID,10, '2026-04-17 09:00:00','2026-04-17 18:30:00','10.0.0.10','10.0.0.10',FALSE,'ON_TIME','ON_TIME',NULL,540, 30,0,0,0,NOW(),NOW()),
  ('2026-04-17', @COMPANY_ID,11, '2026-04-17 09:00:00','2026-04-17 18:00:00','10.0.0.11','10.0.0.11',FALSE,'ON_TIME','ON_TIME',NULL,540,  0,0,0,0,NOW(),NOW()),
  ('2026-04-17', @COMPANY_ID,12, '2026-04-17 09:05:00','2026-04-17 18:00:00','10.0.0.12','10.0.0.12',FALSE,'ON_TIME','ON_TIME',NULL,540,  0,0,0,0,NOW(),NOW()),

/* ===== 2026-04-18 (토) — EMP003 승인 OT 출근 1명 ===== */
  ('2026-04-18', @COMPANY_ID, 3, '2026-04-18 10:00:00','2026-04-18 16:00:00','10.0.0.3','10.0.0.3',FALSE,'HOLIDAY_WORK','HOLIDAY_WORK_END','WEEKLY_OFF',360,360,0,0,360,NOW(),NOW())
;
/* 2026-04-19 (일) — 전원 미출근 */

/* 8. 휴가 유형 + 당일 승인 휴가 ------------------------------------------- */
INSERT INTO vacation_info
  (info_id, company_id, vac_type_name, vac_type_code, is_paid, deduct_days,
   requires_doc, info_is_active, info_is_legal, info_sort_order)
VALUES
  (1, @COMPANY_ID, '연차', 'ANNUAL', TRUE, 1.0, FALSE, TRUE, TRUE, 1)
ON DUPLICATE KEY UPDATE vac_type_name = VALUES(vac_type_name);

/* EMP005 (정하늘, 인사팀 차장) — 2026-04-13 (월) 하루 연차 */
INSERT INTO vacation_req
  (company_id, info_id, emp_id, req_emp_name, req_emp_dept_name,
   vac_req_startat, vac_req_endat, vac_req_use_day, vac_req_reason, vac_req_status,
   manager_id, req_emp_grade, req_emp_title,
   created_at, updated_at)
VALUES
  (@COMPANY_ID, 1, 5, '정하늘', '인사팀',
   '2026-04-13 00:00:00', '2026-04-13 23:59:59', 1.0, '개인사유', 'APPROVED',
   NULL, '차장', NULL, NOW(), NOW());

/* 9. 승인된 초과근무 요청 -------------------------------------------------- */
/* EMP003 토요일 승인 OT (2026-04-18 10:00 ~ 16:00)                            */
/* EMP001 금요일 승인 OT (2026-04-17 18:00 ~ 19:30)                            */
/* EMP008 화요일 승인 OT (2026-04-14 18:00 ~ 20:00)                            */
INSERT INTO overtime_request
  (company_id, emp_id, ot_date, ot_plan_start, ot_plan_end,
   ot_act_start, ot_act_end, ot_reason, ot_status,
   manager_id, approval_doc_id, version,
   created_at, updated_at)
VALUES
  (@COMPANY_ID, 3, '2026-04-18 00:00:00', '2026-04-18 10:00:00', '2026-04-18 16:00:00',
   '2026-04-18 10:00:00', '2026-04-18 16:00:00', '주말 긴급 배포', 'APPROVED', NULL, NULL, 0, NOW(), NOW()),
  (@COMPANY_ID, 1, '2026-04-17 00:00:00', '2026-04-17 18:00:00', '2026-04-17 19:30:00',
   '2026-04-17 18:00:00', '2026-04-17 19:30:00', '릴리즈 작업', 'APPROVED', NULL, NULL, 0, NOW(), NOW()),
  (@COMPANY_ID, 8, '2026-04-14 00:00:00', '2026-04-14 18:00:00', '2026-04-14 20:00:00',
   '2026-04-14 18:00:00', '2026-04-14 20:00:00', '고객 미팅 준비', 'APPROVED', NULL, NULL, 0, NOW(), NOW());

/* =========================================================================
   검증용 빠른 쿼리
   -------------------------------------------------------------------------
   -- 주간현황: 월~일 카운트
   SELECT work_date, COUNT(*) total,
          SUM(CASE WHEN check_in_status='LATE' THEN 1 ELSE 0 END) late_cnt,
          SUM(CASE WHEN check_out_status='EARLY_LEAVE' THEN 1 ELSE 0 END) early_cnt
     FROM commute_record
    WHERE company_id = UUID_TO_BIN('11111111-1111-1111-1111-111111111111')
      AND work_date BETWEEN '2026-04-13' AND '2026-04-19'
    GROUP BY work_date ORDER BY work_date;

   -- 부서별 사원 수
   SELECT d.dept_name, COUNT(*) FROM employee e
     JOIN department d ON e.dept_id = d.dept_id
    WHERE e.company_id = UUID_TO_BIN('11111111-1111-1111-1111-111111111111')
      AND e.emp_status = 'ACTIVE'
    GROUP BY d.dept_name;

   -- 승인 OT 합계 (주간)
   SELECT emp_id,
          SUM(TIMESTAMPDIFF(MINUTE, ot_plan_start, ot_plan_end)) ot_min
     FROM overtime_request
    WHERE company_id = UUID_TO_BIN('11111111-1111-1111-1111-111111111111')
      AND ot_status = 'APPROVED'
      AND ot_date BETWEEN '2026-04-13 00:00:00' AND '2026-04-19 23:59:59'
    GROUP BY emp_id;

   API 호출 예:
   GET /attendance/admin/daily/weekly-stats?weekStart=2026-04-14
   GET /attendance/admin/daily/dept-summary?weekStart=2026-04-14
   GET /attendance/admin/daily/overtime?weekStart=2026-04-14&page=0&size=10
   GET /attendance/admin/daily/period/list?start=2026-04-13&end=2026-04-19&page=0&size=20
   헤더: X-User-Company: 11111111-1111-1111-1111-111111111111
         X-User-Role: HR_ADMIN
   ========================================================================= */
