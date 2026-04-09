-- =============================================
-- ERD에 추가해야 할 컬럼들 (기존 테이블 수정)
-- =============================================

-- 결재 문서: 추가 필드 5개
ALTER TABLE `결재 문서` ADD `emp_dept_id` BIGINT NULL COMMENT '기안자 부서 ID';
ALTER TABLE `결재 문서` ADD `doc_opinion` VARCHAR(255) NULL COMMENT '기안 의견';
ALTER TABLE `결재 문서` ADD `version` BIGINT NULL COMMENT '낙관적 락 버전';
ALTER TABLE `결재 문서` ADD `personal_folder_id` BIGINT NULL COMMENT '개인 문서함 ID';
ALTER TABLE `결재 문서` ADD `dept_folder_id` BIGINT NULL COMMENT '부서 문서함 ID';

-- 결재라인: 추가 필드 1개
ALTER TABLE `결재라인` ADD `emp_dept_id` BIGINT NULL COMMENT '사원 부서 ID';

-- 결재 위임: 추가 필드 5개 (원래 결재자 상세정보 + 사유)
ALTER TABLE `결재 위임` ADD `emp_dept_name` VARCHAR(255) NOT NULL COMMENT '원래 결재자 부서명';
ALTER TABLE `결재 위임` ADD `emp_grade` VARCHAR(255) NOT NULL COMMENT '원래 결재자 직급';
ALTER TABLE `결재 위임` ADD `emp_title` VARCHAR(255) NOT NULL COMMENT '원래 결재자 직책';
ALTER TABLE `결재 위임` ADD `emp_name` VARCHAR(255) NOT NULL COMMENT '원래 결재자 이름';
ALTER TABLE `결재 위임` ADD `reason` VARCHAR(255) NULL COMMENT '위임 사유';

-- 결재 양식 폴더: 추가 필드 2개
ALTER TABLE `결재 양식 폴더` ADD `parent_id` BIGINT NULL COMMENT '부모 폴더 (self FK)';
ALTER TABLE `결재 양식 폴더` ADD `folder_path` VARCHAR(255) NOT NULL COMMENT 'MinIO 경로';

-- 결재 번호 규칙: 추가 필드 2개
ALTER TABLE `결재 번호 규칙` ADD `number_rule_slot3_type` VARCHAR(255) NOT NULL COMMENT '3번째 자리 타입';
ALTER TABLE `결재 번호 규칙` ADD `number_rule_slot3_custom` VARCHAR(255) NULL COMMENT '3번째 자리 직접입력';

-- 결재 라인 템플릿: 추가 필드 1개
ALTER TABLE `결재 라인 템플릿` ADD `is_default` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '기본 결재선 여부';

-- 부서 문서함 설정: 추가 필드 2개
ALTER TABLE `부서 문서함 설정` ADD `folder_name` VARCHAR(100) NOT NULL COMMENT '문서함 이름';
ALTER TABLE `부서 문서함 설정` ADD `sort_order` INT NOT NULL DEFAULT 0 COMMENT '정렬 순서';


-- =============================================
-- Entity에만 있는 신규 테이블 8개
-- =============================================

CREATE TABLE `채번 카운터` (
	`seq_counter_id` BIGINT NOT NULL DEFAULT AUTO_INCREMENT,
	`company_id` UUID NOT NULL,
	`seq_rule_id` BIGINT NOT NULL COMMENT '결재 번호 규칙 FK',
	`seq_reset_key` VARCHAR(20) NOT NULL COMMENT '리셋 주기별 키',
	`seq_current` INT NOT NULL DEFAULT 0 COMMENT '현재 일련번호',
	`seq_version` INT NOT NULL DEFAULT 1 COMMENT '낙관적 락 버전',
	`created_at` DATETIME NOT NULL,
	`updated_at` DATETIME NULL
);

ALTER TABLE `채번 카운터` ADD CONSTRAINT `PK_채번 카운터` PRIMARY KEY (
	`seq_counter_id`
);

CREATE TABLE `결재 상태 이력` (
	`history_id` BIGINT NOT NULL DEFAULT AUTO_INCREMENT,
	`doc_id` BIGINT NOT NULL COMMENT '결재 문서 FK',
	`company_id` UUID NOT NULL,
	`previous_status` VARCHAR(255) NULL COMMENT '이전 상태',
	`changed_status` VARCHAR(255) NOT NULL COMMENT '변경된 상태',
	`changed_by` BIGINT NOT NULL COMMENT '변경자 사원 ID',
	`change_by_name` VARCHAR(255) NOT NULL COMMENT '변경자 이름',
	`change_by_dept_name` VARCHAR(255) NOT NULL COMMENT '변경자 부서명',
	`change_by_grade` VARCHAR(255) NOT NULL COMMENT '변경자 직급',
	`change_reason` VARCHAR(255) NULL COMMENT '변경 사유',
	`changed_at` DATETIME NOT NULL COMMENT '변경 일시',
	`created_at` DATETIME NOT NULL,
	`updated_at` DATETIME NULL
);

ALTER TABLE `결재 상태 이력` ADD CONSTRAINT `PK_결재 상태 이력` PRIMARY KEY (
	`history_id`
);

CREATE TABLE `결재 첨부파일` (
	`attach_id` BIGINT NOT NULL DEFAULT AUTO_INCREMENT,
	`doc_id` BIGINT NOT NULL COMMENT '결재 문서 FK',
	`company_id` UUID NOT NULL,
	`file_name` VARCHAR(255) NOT NULL COMMENT '원본 파일명',
	`file_size` BIGINT NOT NULL COMMENT '파일 크기 (bytes)',
	`object_name` VARCHAR(255) NOT NULL COMMENT 'MinIO 오브젝트명',
	`content_type` VARCHAR(255) NOT NULL COMMENT '파일 MIME 타입',
	`created_at` DATETIME NOT NULL,
	`updated_at` DATETIME NULL
);

ALTER TABLE `결재 첨부파일` ADD CONSTRAINT `PK_결재 첨부파일` PRIMARY KEY (
	`attach_id`
);

CREATE TABLE `즐겨찾는 양식` (
	`frequent_form_id` BIGINT NOT NULL DEFAULT AUTO_INCREMENT,
	`company_id` UUID NOT NULL,
	`emp_id` BIGINT NOT NULL COMMENT '사원 ID',
	`form_id` BIGINT NOT NULL COMMENT '결재 양식 FK',
	`created_at` DATETIME NOT NULL,
	`updated_at` DATETIME NULL
);

ALTER TABLE `즐겨찾는 양식` ADD CONSTRAINT `PK_즐겨찾는 양식` PRIMARY KEY (
	`frequent_form_id`
);

CREATE TABLE `개인 결재 문서함` (
	`personal_folder_id` BIGINT NOT NULL DEFAULT AUTO_INCREMENT,
	`company_id` UUID NOT NULL,
	`emp_id` BIGINT NOT NULL COMMENT '사원 ID',
	`folder_name` VARCHAR(100) NOT NULL COMMENT '폴더명',
	`sort_order` INT NOT NULL DEFAULT 0 COMMENT '정렬 순서',
	`is_active` BOOLEAN NOT NULL DEFAULT TRUE COMMENT '활성 여부',
	`created_at` DATETIME NOT NULL,
	`updated_at` DATETIME NULL
);

ALTER TABLE `개인 결재 문서함` ADD CONSTRAINT `PK_개인 결재 문서함` PRIMARY KEY (
	`personal_folder_id`
);

CREATE TABLE `부서 문서함 관리자` (
	`manager_id` BIGINT NOT NULL DEFAULT AUTO_INCREMENT,
	`dept_app_folder_id` BIGINT NOT NULL COMMENT '부서 문서함 FK',
	`company_id` UUID NOT NULL,
	`emp_id` BIGINT NOT NULL COMMENT '관리자 사원 ID',
	`emp_name` VARCHAR(255) NOT NULL COMMENT '관리자 이름',
	`dept_name` VARCHAR(255) NOT NULL COMMENT '부서명',
	`created_at` DATETIME NOT NULL,
	`updated_at` DATETIME NULL
);

ALTER TABLE `부서 문서함 관리자` ADD CONSTRAINT `PK_부서 문서함 관리자` PRIMARY KEY (
	`manager_id`
);

CREATE TABLE `개인 폴더 문서` (
	`id` BIGINT NOT NULL DEFAULT AUTO_INCREMENT,
	`company_id` UUID NOT NULL,
	`emp_id` BIGINT NOT NULL COMMENT '사원 ID',
	`doc_id` BIGINT NOT NULL COMMENT '결재 문서 FK',
	`personal_folder_id` BIGINT NOT NULL COMMENT '개인 폴더 FK',
	`created_at` DATETIME NOT NULL,
	`updated_at` DATETIME NULL
);

ALTER TABLE `개인 폴더 문서` ADD CONSTRAINT `PK_개인 폴더 문서` PRIMARY KEY (
	`id`
);

CREATE TABLE `자동 분류 규칙` (
	`rule_id` BIGINT NOT NULL DEFAULT AUTO_INCREMENT,
	`company_id` UUID NOT NULL,
	`emp_id` BIGINT NOT NULL COMMENT '사원 ID',
	`source_box` VARCHAR(255) NOT NULL COMMENT '발신함/수신함 (SENT/INBOX)',
	`rule_name` VARCHAR(255) NOT NULL COMMENT '규칙 이름',
	`title_contains` VARCHAR(255) NULL COMMENT '제목 포함 키워드',
	`form_name` VARCHAR(255) NULL COMMENT '양식명 조건',
	`drafter_dept` VARCHAR(255) NULL COMMENT '기안자 부서 조건',
	`drafter_name` VARCHAR(255) NULL COMMENT '기안자 이름 조건',
	`target_folder_id` BIGINT NOT NULL COMMENT '대상 폴더 FK',
	`is_active` BOOLEAN NOT NULL DEFAULT TRUE COMMENT '활성 여부',
	`sort_order` INT NOT NULL DEFAULT 0 COMMENT '우선순위',
	`created_at` DATETIME NOT NULL,
	`updated_at` DATETIME NULL
);

ALTER TABLE `자동 분류 규칙` ADD CONSTRAINT `PK_자동 분류 규칙` PRIMARY KEY (
	`rule_id`
);
