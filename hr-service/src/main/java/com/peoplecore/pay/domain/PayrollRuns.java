package com.peoplecore.pay.domain;

import com.peoplecore.company.domain.Company;
import com.peoplecore.pay.enums.PayrollStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "payroll_runs",   //급여산정
        indexes = {
                @Index(name = "idx_payroll_company_month", columnList = "company_id, pay_year_month")
        })
public class PayrollRuns {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long payrollRunId;

    @Column(nullable = false, length = 7)
    private String payYearMonth;

//    대상직원수
    private Integer totalEmployees;
    private Long totalPay;
    private Long totalDeduction;
    private Long totalNetPay;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayrollStatus payrollStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    private LocalDate payDate;

    private Long approvalDocId; // 전자결재 문서 ID (결재 상신 시 저장)

    @Column
    private Long totalIndustrialAccident;    // 회사 산재료 합계 (월별, 회사 100% 부담)



//    합계 갱신
    public void updateTotals(int empCount, long totalPay, long totalDeduction, long netPay){
        this.totalEmployees = empCount;
        this.totalPay = totalPay;
        this.totalDeduction = totalDeduction;
        this.totalNetPay = netPay;
    }

    // CALCULATING -> CONFIRMED -> PENDING_APPROVAL -> APPROVED -> PAID
//    상태변경: 확정
    public void confirm(){
        if(this.payrollStatus != PayrollStatus.CALCULATING){
            throw new IllegalStateException("산정중 산태에서만 확정 가능합니다.");
        }
        this.payrollStatus = PayrollStatus.CONFIRMED;
    }

//    상태변경: 전자결재 상신
    public void submitApproval(Long approvalDocId){
        if (this.payrollStatus != PayrollStatus.CALCULATING
                && this.payrollStatus != PayrollStatus.CONFIRMED){
            throw new IllegalStateException("산정중 또는 확정 상태에서만 전자결재 상신 가능합니다.");
        }
        this.approvalDocId = approvalDocId;
        this.payrollStatus = PayrollStatus.PENDING_APPROVAL;
    }

//    상태변경: 전자결재 승인완료
    public void approve(){
        if(this.payrollStatus != PayrollStatus.PENDING_APPROVAL){
            throw new IllegalStateException("전자결재 진행중 상태에서만 승인 가능합니다.");
        }
        this.payrollStatus = PayrollStatus.APPROVED;
    }

//    상태 변경: 지급완료
    public void markPaid(LocalDate payDate){
        if (this.payrollStatus != PayrollStatus.APPROVED){
            throw new IllegalStateException("승인완료 상태에서만 지급처리 가능합니다.");
        }
        this.payrollStatus = PayrollStatus.PAID;
        this.payDate = payDate;
    }

//    상태변경: 전자결재 반려
    public void rejectApproval(){
        if (this.payrollStatus != PayrollStatus.PENDING_APPROVAL){
            throw new IllegalStateException("전자결재 진행중 상태에서만 반려 가능합니다.");
        }
        this.payrollStatus = PayrollStatus.CONFIRMED;
    }

//    kafka 이벤트 수신 시 approvalDocId 바인딩
//    - Kafka 발행 후 collab이 INSERT한 문서 ID를 역으로 주입받는 경로
//    - 이미 바인딩된 경우(중복 이벤트)는 무시
    public  void bindApprovalDoc(Long approvalDocId) {
        if (this.approvalDocId == null && approvalDocId != null) {
            this.approvalDocId = approvalDocId;
        }
    }

//   결재 회수
    public void cancelApproval(){
        if (this.payrollStatus != PayrollStatus.PENDING_APPROVAL){
            throw new IllegalStateException("전자결재 진행중 상태에서만 회수 가능합니다.");
        }
        this.payrollStatus = PayrollStatus.CONFIRMED;
        this.approvalDocId = null;
    }

    public void setIndustrialAccidentTotal(Long total) {
        this.totalIndustrialAccident = total;
    }



}
