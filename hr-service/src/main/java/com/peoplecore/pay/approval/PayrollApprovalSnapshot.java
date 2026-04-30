package com.peoplecore.pay.approval;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payroll_approval_snapshot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PayrollApprovalSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long snapshotId;

    @Column(nullable = false, unique = true)
    private Long approvalDocId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApprovalFormType approvalType;

    private Long payrollRunId;
    private Long sevId;

    @Column(nullable = false)
    private UUID companyId;

    @Lob
    @Column(nullable = false)
    private String htmlSnapshot;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
