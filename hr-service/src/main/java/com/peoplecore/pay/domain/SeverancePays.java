package com.peoplecore.pay.domain;

import com.peoplecore.company.domain.Company;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.entity.BaseTimeEntity;
import com.peoplecore.pay.enums.RetirementType;
import com.peoplecore.pay.enums.SevStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "severance_pays",     //퇴직금 지급
        indexes = {
                @Index(name = "idx_severance_company", columnList = "company_id, sev_status"),
                @Index(name = "idx_severance_emp", columnList = "emp_id, resign_date")
        })
public class SeverancePays extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long sevId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emp_id", nullable = false)
    private Employee employee;      //퇴직 대상 사원

    @Column(nullable = false)
    private LocalDate hireDate;     //입사일 - 스냅샷 용

    @Column(nullable = false)
    private LocalDate resignDate;

//    퇴직제도유형
    @Enumerated(EnumType.STRING)
    @Column(nullable  = false)
    private RetirementType retirementType;

//    근속연수
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal serviceYears;

//    근속일수
    @Column(nullable = false)
    private Long serviceDays;

///     산정 기초 데이터
//    최근 3개월 급여 총액
    @Column(nullable = false)
    private Long last3MonthPay;

//    직전1년 상여금 총액
    private Long lastYearBonus;

    ///    연차수당 (평균임금 반영분 과 퇴직정산 별도지급분 분리)
//    평균임금 산정용역 연차수당
    private Long annualLeaveForAvgWage;
//    퇴직정산 별도지급 연차수당
    @Builder.Default
    private Long annualLeaveOnRetirement = 0L;

//    직전 3개월 총 일수
    @Column(nullable = false)
    private Integer last3MonthDays;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal avgDailyWage;

///     산정금액
//    퇴직금 산정액
    @Column(nullable = false)
    private Long severanceAmount;

//    퇴직소득세
    @Column(nullable = false)
    @Builder.Default
    private Long taxAmount = 0L;

//    지방소득세 (퇴직소득세 * 10%)
    @Column(nullable = false)
    @Builder.Default
    private Long localIncomeTax = 0L;

//    세액산출에 사용된 귀속연도
    @Column(nullable = false)
    @Builder.Default
    private Integer taxYear = 0;

//    IRP 이전여부 - true시 과세이연(세액 0원)
    @Column(nullable = false)
    @Builder.Default
    private Boolean irpTransfer = false;

//    실지급액 (산정액 - 세금)
    @Column(nullable = false)
    private Long netAmount;

///    DC형 : 기 적릭금 차감
//    DC 기적립 합계
    @Builder.Default
    private Long dcDepositedTotal = 0L;

//    DC 차액 (산정액 - 기적립)
    @Builder.Default
    private Long dcDiffAmount = 0L;


//    퇴직금상태
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SevStatus sevStatus = SevStatus.CALCULATING;

//    전자결재 문서 ID
    private Long approvalDocId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

//    지급일
    private LocalDate transferDate; //실제 이체일

//    확정자
    private Long confirmedBy;
    private LocalDateTime confirmedAt;
//    지급처리자
    private Long paidBy;
    private LocalDateTime paidAt;    //지급처리 버튼을 누른 시각

//    스냅샷
    @Column(length = 50)
    private String empName;     // 사원명 스냅샷
    @Column(length = 100)
    private String deptName;    // 부서명 스냅샷
    @Column(length = 50)
    private String gradeName;   // 직급명 스냅샷
    @Column(length = 100)
    private String workGroupName;   //근무그룹명 스냅샷





//    확정
    public void confirm(Long confirmedBy){
        if (this.sevStatus != SevStatus.CALCULATING) {
            throw new IllegalStateException("산정중 상태에서만 확정 가능합니다");
        }
        this.sevStatus = SevStatus.CONFIRMED;
        this.confirmedBy = confirmedBy;
        this.confirmedAt = LocalDateTime.now();
    }

//    전자결재 상신
    public void submitApproval(Long approvalDocId){
        if (this.sevStatus != SevStatus.CONFIRMED){
            throw new IllegalStateException("확정 상태에서만 전자결재 상신 가능합니다.");
        }
        this.approvalDocId = approvalDocId;
        this.sevStatus = SevStatus.PENDING_APPROVAL;
    }

//    전자결재 승인
    public void approve(){
        if (this.sevStatus != SevStatus.PENDING_APPROVAL){
            throw new IllegalStateException("전자결재 진행중 상태에서만 승인 가능합니다.");
        }
        this.sevStatus = SevStatus.APPROVED;
    }

//    전자결재 반려 -> CONFIRMED 복귀
    public void rejectApproval(){
        if (this.sevStatus != SevStatus.PENDING_APPROVAL){
            throw new IllegalStateException("전자결재 진행중 상태에서만 반려 가능합니다.");
        }
        this.sevStatus = SevStatus.CONFIRMED;
        this.approvalDocId = null;
    }

//    approvalDocId 보완
    public void bindApprovalDoc(Long approvalDocId){
        this.approvalDocId = approvalDocId;
    }

//    전자결재 회수
    public void cancelApproval(){
        if(this.sevStatus != SevStatus.PENDING_APPROVAL){
            throw new IllegalStateException("전자결재 진행중 상태에서만 회수 가능합니다.");
        }
        this.sevStatus = SevStatus.CONFIRMED;
        this.approvalDocId = null;
    }

//    지급 완료
    public void markPaid(Long paidBy, LocalDate transferDate){
        if (this.sevStatus != SevStatus.APPROVED){
            throw new IllegalStateException("승인완료 상태에서만 지급처리 가능합니다.");
        }
        this.sevStatus = SevStatus.PAID;
        this.paidBy = paidBy;
        this.paidAt = LocalDateTime.now();
        this.transferDate = transferDate;
    }

//    세금 설정
// netAmount = (퇴직금 산정액 − 퇴직소득세 − 지방소득세) + 퇴직정산 연차수당 별도지급분
//    별도지급 연차수당은 근로소득으로 처리되므로 여기서 과세 X
    public void applyTax(Long taxAmount, Long localIncomeTax, Integer taxYear, Boolean irpTransfer){
        this.taxAmount = taxAmount;
        this.localIncomeTax = localIncomeTax;
        this.taxYear = taxYear;
        this.irpTransfer = irpTransfer;
        this.netAmount = this.severanceAmount - taxAmount - localIncomeTax + this.annualLeaveOnRetirement;
    }

//    재산정 (CALCULATING)
    public void recalculate(Long last3MonthPay, Long lastYearBonus, Long annualLeaveForAvgWage, Long annualLeaveOnRetirement, Integer last3MonthDays, BigDecimal avgDailyWage, Long severanceAmount, Long dcDepositedTotal, Long dcDiffAmount){
        if (this.sevStatus != SevStatus.CALCULATING){
            throw new IllegalStateException("산정중 상태에서만 재산정 가능합니다.");
        }
        this.last3MonthPay = last3MonthPay;
        this.lastYearBonus = lastYearBonus;
        this.annualLeaveForAvgWage = annualLeaveForAvgWage;
        this.annualLeaveOnRetirement = annualLeaveOnRetirement;
        this.last3MonthDays = last3MonthDays;
        this.avgDailyWage = avgDailyWage;
        this.severanceAmount = severanceAmount;
//        실지급 netAmount = (퇴직금 - 세금) + 퇴직정산 연차수당 별도지급분
        this.netAmount = severanceAmount - this.taxAmount + annualLeaveOnRetirement;
        this.dcDepositedTotal = dcDepositedTotal;
        this.dcDiffAmount = dcDiffAmount;
    }


}
