package com.peoplecore.pay.domain;

import com.peoplecore.pay.enums.HistoryStatus;
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
@Table(name = "payroll_run_histories")  //급여산정이력
public class PayrollRunHistories {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long payrollHistoryId;

    @Column(nullable = false)
    private Long payrollRunId;

//    산정자
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
