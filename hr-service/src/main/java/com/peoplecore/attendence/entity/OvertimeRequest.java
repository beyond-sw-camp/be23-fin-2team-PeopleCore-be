package com.peoplecore.attendence.entity;

import com.peoplecore.employee.domain.Employee;
import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 초과근무 신청
 */
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(
        name = "overtime_request",
        indexes = {
                @Index(name = "idx_ot_req_company_status",
                        columnList = "company_id, ot_status"),
                @Index(name = "idx_ot_req_emp_date",
                        columnList = "emp_id, ot_date")
        }
)
public class OvertimeRequest extends BaseTimeEntity {

    /**
     * 초과근무신청 ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long otId;

    /* 회사 Id*/
    @Column(nullable = false)
    private UUID companyId;

    /**
     * 사원 아이디
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emp_id", nullable = false)
    private Employee employee;

    /**
     * 신청 날짜
     */
    @Column(nullable = false)
    private LocalDateTime otDate;

    /**
     * 초과 근무 시작 예정 시간
     */
    @Column(nullable = false)
    private LocalDateTime otPlanStart;

    /**
     * 초과 근무 종료예정 시간
     */
    @Column(nullable = false)
    private LocalDateTime otPlanEnd;

    /**
     * 초과 근무 실제 시작 시간
     */
    private LocalDateTime otActStart;

    /**
     * 초과 근무 실제 종료 시간
     */
    private LocalDateTime otActEnd;

    /**
     * 초과 근무 사유
     */
    @Column(nullable = false)
    private String otReason;

    /**
     * 초과 근무 신청 상태
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OtStatus otStatus;

    /**
     * 처리자 사원 id
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private Employee manager;

    /**
     * 낙관적 락 - 승인/반려 동시 처리 방지
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /**
     * 승인/반려/취소 처리 캡슐화
     */
    public void process(OtStatus newStatus, Employee manager) {
        this.otStatus = newStatus;
        this.manager = manager;
    }

    /**
     * 실제 초과근무 시작/종료 기록
     */
    public void recordActual(LocalDateTime start, LocalDateTime end) {
        this.otActStart = start;
        this.otActEnd = end;
    }
}
