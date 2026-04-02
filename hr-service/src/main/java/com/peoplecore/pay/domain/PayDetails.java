package com.peoplecore.pay.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder
@Table(name = "pay_detail")
public class PayDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long payDetailsId;

    @Column(nullable = false)
    private Long payrollRunId;

    @Column(nullable = false)
    private Long empId;

    @Column(nullable = false)
    private Long payItemId;

    @Column(nullable = false)
    private Long amount;

    private String memo;

    @Column(nullable = false)
    private UUID companyId;

}
