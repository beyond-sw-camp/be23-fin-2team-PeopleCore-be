package com.peoplecore.pay.approval;

import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.pay.domain.PayrollRuns;
import com.peoplecore.pay.enums.PayItemType;
import com.peoplecore.pay.enums.PayrollEmpStatusType;
import com.peoplecore.pay.enums.PayrollStatus;
import com.peoplecore.pay.repository.PayrollDetailsRepository;
import com.peoplecore.pay.repository.PayrollEmpStatusRepository;
import com.peoplecore.pay.repository.PayrollRunsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static com.peoplecore.pay.approval.ApprovalFormatter.*;

@Service
@Slf4j
@Transactional(readOnly = true)
public class PayrollApprovalDraftService {

    private final PayrollRunsRepository payrollRunsRepository;
    private final PayrollDetailsRepository payrollDetailsRepository;
    private final EmployeeRepository employeeRepository;
//    private final PayrollApprovalDocCreatedPublisher docCreatedPublisher;
    private final ApprovalFormCache approvalFormCache;
    private final PayrollEmpStatusRepository payrollEmpStatusRepository;

    @Autowired
    public PayrollApprovalDraftService(PayrollRunsRepository payrollRunsRepository, PayrollDetailsRepository payrollDetailsRepository, EmployeeRepository employeeRepository,  ApprovalFormCache approvalFormCache, PayrollEmpStatusRepository payrollEmpStatusRepository) {
        this.payrollRunsRepository = payrollRunsRepository;
        this.payrollDetailsRepository = payrollDetailsRepository;
        this.employeeRepository = employeeRepository;
        this.approvalFormCache = approvalFormCache;
        this.payrollEmpStatusRepository = payrollEmpStatusRepository;
    }

    private static final DateTimeFormatter YMD = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter YM =DateTimeFormatter.ofPattern("yyyy-MM");


//    전자결재 미리보기 데이터 조회 (htmlTemplate + dataMap)
    public ApprovalDraftResDto draft(UUID companyId, Long userId, Long payrollRunId){
        PayrollRuns run = payrollRunsRepository.findByPayrollRunIdAndCompany_CompanyId(payrollRunId, companyId).orElseThrow(()-> new CustomException(ErrorCode.PAYROLL_NOT_FOUND));

//        결재 가능 상태 검증 (Confirmed 또는 PENDING_APPROVAL(재상신 케이스), CALCULATING(사원별 일부 확정후 부분결재) 가능)
        if (run.getPayrollStatus() != PayrollStatus.CALCULATING
                && run.getPayrollStatus() != PayrollStatus.CONFIRMED
                && run.getPayrollStatus() != PayrollStatus.PENDING_APPROVAL){
            throw new CustomException(ErrorCode.PAYROLL_STATUS_INVALID);
        }

        Employee drafter = employeeRepository.findById(userId).orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

        ApprovalFormCache.CachedForm form = approvalFormCache.get(companyId, ApprovalFormType.SALARY);
        String htmlTemplate = form.formHtml();   // ← collab → MinIO 최신본
        Map<String, String> dataMap = buildDataMap(run, drafter);

        return ApprovalDraftResDto.builder()
                .type(ApprovalFormType.SALARY)
                .ledgerId(payrollRunId)
                .htmlTemplate(htmlTemplate)
                .dataMap(dataMap)
                .build();
    }

    private Map<String, String> buildDataMap(PayrollRuns run, Employee drafter) {
        Map<String, String> m = new HashMap<>();
        Long runId = run.getPayrollRunId();

        // 확정된 사원 ID Set 미리 추출
        Set<Long> confirmedEmpIds = payrollEmpStatusRepository
                .findByPayrollRuns_PayrollRunIdAndStatus(runId, PayrollEmpStatusType.CONFIRMED)
                .stream()
                .map(p -> p.getEmployee().getEmpId())
                .collect(Collectors.toSet());

        if (confirmedEmpIds.isEmpty()) {
            throw new CustomException(ErrorCode.NO_CONFIRMED_EMPLOYEES);
        }
        // 헤더
        m.put("drafterName", drafter.getEmpName());
        m.put("drafterDept", drafter.getDept() != null ? drafter.getDept().getDeptName() : "");
        m.put("draftDate",   LocalDate.now().format(YMD));
        m.put("docNo",       String.format("PAY-%s-%d", run.getPayYearMonth(), runId));
        m.put("approvalLineHtml", "");  //프론트에서 결재선 선택후 채움

        // 지급 기본 정보
        m.put("payMonth",         run.getPayYearMonth());
        m.put("payScheduledDate", date(run.getPayDate()));
        // 확정된 사원 수만
        m.put("payHeadcount", confirmedEmpIds.size() + "명");

        // PayItem별 합계 조회 (확정 사원만, 지급/공제 분리)
        List<PayrollItemSummaryDto> paymentSummary = payrollDetailsRepository
                .summarizeByPayItemForEmployees(runId, PayItemType.PAYMENT, confirmedEmpIds);
        List<PayrollItemSummaryDto> deductionSummary = payrollDetailsRepository
                .summarizeByPayItemForEmployees(runId, PayItemType.DEDUCTION, confirmedEmpIds);

        // 지급 — 과세/비과세 분리
        Map<String, Long> taxable = new HashMap<>();
        Map<String, Long> nonTaxable = new HashMap<>();
        for (PayrollItemSummaryDto row : paymentSummary) {
            if (Boolean.TRUE.equals(row.isTaxable())) {
                taxable.put(row.payItemName(), row.totalAmount());
            } else {
                nonTaxable.put(row.payItemName(), row.totalAmount());
            }
        }

        // 공제 — 단일 Map
        Map<String, Long> deduction = new HashMap<>();
        for (PayrollItemSummaryDto row : deductionSummary) {
            deduction.put(row.payItemName(), row.totalAmount());
        }

        // ── 지급상세 (항목별 과세/비과세) ──
        m.put("baseSalaryTaxable",              currency(taxable.getOrDefault("기본급", 0L)));
        m.put("baseSalaryNonTaxable",           currency(nonTaxable.getOrDefault("기본급", 0L)));
        m.put("bonusTaxable",                   currency(taxable.getOrDefault("상여금", 0L)));
        m.put("bonusNonTaxable",                currency(nonTaxable.getOrDefault("상여금", 0L)));
        m.put("nightAllowanceTaxable",          currency(taxable.getOrDefault("야간근로수당", 0L)));
        m.put("nightAllowanceNonTaxable",       currency(nonTaxable.getOrDefault("야간근로수당", 0L)));
        m.put("overtimeAllowanceTaxable",       currency(taxable.getOrDefault("연장근로수당", 0L)));
        m.put("overtimeAllowanceNonTaxable",    currency(nonTaxable.getOrDefault("연장근로수당", 0L)));
        m.put("annualLeaveAllowanceTaxable",    currency(taxable.getOrDefault("연차수당", 0L)));
        m.put("annualLeaveAllowanceNonTaxable", currency(nonTaxable.getOrDefault("연차수당", 0L)));
        m.put("holidayAllowanceTaxable",        currency(taxable.getOrDefault("휴일근로수당", 0L)));
        m.put("holidayAllowanceNonTaxable",     currency(nonTaxable.getOrDefault("휴일근로수당", 0L)));
        m.put("educationSupportTaxable",        currency(taxable.getOrDefault("교육비지원금", 0L)));
        m.put("educationSupportNonTaxable",     currency(nonTaxable.getOrDefault("교육비지원금", 0L)));
        m.put("mealAllowanceTaxable",           currency(taxable.getOrDefault("식대", 0L)));
        m.put("mealAllowanceNonTaxable",        currency(nonTaxable.getOrDefault("식대", 0L)));

        // 지급 소계·합계
        long taxablePayment    = taxable.values().stream().mapToLong(Long::longValue).sum();
        long nonTaxablePayment = nonTaxable.values().stream().mapToLong(Long::longValue).sum();
        long totalPayment      = taxablePayment + nonTaxablePayment;

        m.put("paymentSubtotalTaxable",    currency(taxablePayment));
        m.put("paymentSubtotalNonTaxable", currency(nonTaxablePayment));
        m.put("paymentTotal",              currency(totalPayment));

        // ── 공제상세 ──
        m.put("healthInsurance",      currency(deduction.getOrDefault("건강보험", 0L)));
        m.put("employmentInsurance",  currency(deduction.getOrDefault("고용보험", 0L)));
        m.put("nationalPension",      currency(deduction.getOrDefault("국민연금", 0L)));
        m.put("incomeTax",            currency(deduction.getOrDefault("근로소득세", 0L)));
        m.put("localIncomeTax",       currency(deduction.getOrDefault("근로지방소득세", 0L)));
        m.put("studentLoanRepayment", currency(deduction.getOrDefault("학자금상환", 0L)));

        long totalDeduction = deduction.values().stream().mapToLong(Long::longValue).sum();
        m.put("deductionTotal", currency(totalDeduction));

        // ── 헤더 totalPayAmount (실지급액) ──
        m.put("totalPayAmount", currency(totalPayment - totalDeduction));

        return m;
    }


}
