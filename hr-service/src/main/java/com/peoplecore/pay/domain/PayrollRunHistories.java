package com.peoplecore.pay.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "payroll_run_histories")
public class PayrollRunHistories {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long payHistoryId;

    @Column(nullable = false)
    private Long payrollRunId;

    @Column(nullable = false)
    private Long processedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HistoryStatus historyStatus;

    private LocalDateTime processedAt;
    private String memo;

    @Column(nullable = false)
    private UUID companyId;

}
