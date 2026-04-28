package com.peoplecore.attendance.entity;

import com.peoplecore.employee.domain.Employee;
import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/*
 * 근태 정정 요청 테이블
 */
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(
        name = "attendence_modify",
        indexes = {
                // HR 관리자 목록 — 회사 내 상태 필터링
                @Index(name = "idx_atten_modify_company_status",
                        columnList = "company_id, atten_status"),
                // 사원 본인 이력 + comRecId 기준 중복 체크
                @Index(name = "idx_atten_modify_emp",
                        columnList = "emp_id, work_date"),
                // result 이벤트 수신 시 docId 로 단건 조회
                @Index(name = "idx_atten_modify_doc_id",
                        columnList = "approval_doc_id")
        }
)
public class AttendanceModify extends BaseTimeEntity {

    /**
     * 근태 정정 요청 ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long attenModiId;

    /**
     * 회사 ID
     */
    @Column(nullable = false)
    private UUID companyId;

    /**
     * 요청 사원 아이디
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emp_id", nullable = false)
    private Employee employee;

    /*
     * CommuteRecord PK - attendance 논리 참조 (FK 제약 없음).
     * 휴일 근무 미입력 등으로 CommuteRecord 가 아직 없는 상태에서 정정 신청 가능 → nullable.
     * 승인 시 null 이면 신규 INSERT, 아니면 기존 UPDATE.
     */
    private Long comRecId;

    /**
     * 대상 근무 일자 - 복합키 두번째 구성 요소
     */
    @Column(nullable = false)
    private LocalDate workDate;

    /**
     * 요청 사원 이름 요청 시점 스냅샷
     */
    @Column(name = "atten_emp_name", nullable = false, length = 50)
    private String attenEmpName;

    /**
     * 요청 사원 부서
     */
    @Column(name = "atten_emp_dept_name", nullable = false, length = 100)
    private String attenEmpDeptName;

    /**
     * 요청 사원 직급
     */
    @Column(name = "atten_emp_grade", nullable = false, length = 50)
    private String attenEmpGrade;

    /**
     * 요청 사원 직책
     */
    @Column(name = "atten_emp_title", nullable = false, length = 50)
    private String attenEmpTitle;

    /**
     * 요청 출근 시간
     */
    private LocalDateTime attenReqCheckIn;

    /**
     * 요청 퇴근 시간
     */
    private LocalDateTime attenReqCheckOut;

    /**
     * 정정 사유
     */
    @Column(nullable = false)
    private String attenReason;

    /* 수정 처리 상태 - 인사과 등록/대기,승인,반려 default==대기 */
    /*
     * 처리 상태 - 대기/승인/반려, default 대기.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "atten_status", nullable = false, length = 20)
    private ModifyStatus attenStatus;

    /**
     * 처리자 id
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atten_manager_id")
    private Employee manager;

    /**
     * 반려 사유 — REJECTED 상태에서만 세팅
     */
    @Column(name = "atten_reject_reason", length = 500)
    private String attenRejectReason;

    /*
     * collaboration-service 결재 문서 ID.
     * docCreated 이벤트 수신 시점에 함께 세팅 → result 이벤트에서 docId 로 역조회.
     */
    @Column(name = "approval_doc_id", nullable = false)
    private Long approvalDocId;


    /**
     * 낙관적 락 - 승인/반려 동시 처리 방지
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /*
     * 승인 처리 — Kafka result Consumer 에서 호출.
     * PENDING → APPROVED 전이만 허용.
     */
    public void approve(Employee manager) {
        this.attenStatus.validateTransitionTo(ModifyStatus.APPROVED);
        this.attenStatus = ModifyStatus.APPROVED;
        this.manager = manager;
    }

    /*
     * 반려 처리 — Kafka result Consumer 에서 호출.
     * PENDING → REJECTED 전이만 허용.
     *      */
    public void reject(Employee manager, String rejectReason) {
        this.attenStatus.validateTransitionTo(ModifyStatus.REJECTED);
        this.attenStatus = ModifyStatus.REJECTED;
        this.manager = manager;
        this.attenRejectReason = rejectReason;
    }

    /*
     * 취소 처리 — 기안자 회수 이벤트 수신 시 Kafka Consumer 에서 호출.
     * PENDING → CANCELED 전이만 허용.
     */
    public void cancel() {
        this.attenStatus.validateTransitionTo(ModifyStatus.CANCELED);
        this.attenStatus = ModifyStatus.CANCELED;
    }
}
