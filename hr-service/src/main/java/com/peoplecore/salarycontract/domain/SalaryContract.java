package com.peoplecore.salarycontract.domain;

import com.peoplecore.employee.domain.Employee;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "salary_contract")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SalaryContract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "contract_id")
    private Long contractId; //대상 사원(인적사항 Employee Join으로 조회)


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="emp_id", nullable = false)
    private Employee employee;

    @Column(name = "company_id",nullable = false)
    private UUID companyId;

    @Column(name = "file_name")
    private String fileName; //첨부파일명

    @OneToMany(mappedBy =  "contract", cascade = CascadeType.ALL)
    private List<SalaryContractDetail> details; //급여상세

    @Column(name = "create_by", nullable = false)
    private Long createBy; //작성자명

    @Column(name = "contract_year", nullable = false)
    private Integer contractYear; //계약 연도(목록 필터)

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private ContractStatus status = ContractStatus.DRAFT; //계약 상태(draft,sent,signed)

    @Column(name = "apply_from")
    private LocalDate applyFrom; //계약 적용 시작일

    @Column(name = "apply_to")
    private LocalDate applyTo; //계약 적용 종료일(정규직null)

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

//    동적 폼 입력값
    @Column(name ="form_values", columnDefinition = "JSON")
    private String formValues;

    //    등록 시점 폼 설정 스냅샷
    @Column(name = "form_snapshot",columnDefinition = "JSON")
    private String formSnapshot;

//    폼 버전
    @Column(name = "form_version")
    private Long formVersion;


}
