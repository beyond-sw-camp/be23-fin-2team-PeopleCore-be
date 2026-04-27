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
    private String fileName; //MinIO 객체 키 (folder/uuid_원본파일명)

    @Column(name = "original_file_name")
    private String originalFileName; //사용자에게 보여줄 원본 파일명

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "file_size")
    private Long fileSize;

    @OneToMany(mappedBy =  "contract", cascade = CascadeType.ALL)
    private List<SalaryContractDetail> details; //급여상세

    @Column(name = "create_by", nullable = false)
    private Long createBy; //작성자명

    @Column(name = "contract_year", nullable = false)
    private Integer contractYear; //계약 연도(목록 필터)

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

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

//    soft delete 삭제일
    @Column(name = "delete_at")
    private LocalDate deletedAt;

//    soft delete처리(삭제일에 현재 날짜 세팅)
    public void softDelete(){
        this.deletedAt = LocalDate.now();
    }

//    삭제 여부 확인 (deleteAt null아닐 시 = 삭제된 상태)
    public boolean isDeleted(){
        return this.deletedAt !=null;
    }

}
