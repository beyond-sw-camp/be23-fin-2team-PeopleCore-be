package com.peoplecore.pay.approval;

import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.pay.domain.SeverancePays;
import com.peoplecore.pay.enums.SevStatus;
import com.peoplecore.pay.repository.SeverancePaysRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.peoplecore.pay.approval.ApprovalFormatter.*;

@Service
@Slf4j
@Transactional(readOnly = true)
public class SeveranceApprovalDraftService {

    private final SeverancePaysRepository severancePaysRepository;
    private final EmployeeRepository employeeRepository;
    private final ApprovalFormCache approvalFormCache;

    private static final DateTimeFormatter YMD = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter YM =DateTimeFormatter.ofPattern("yyyy-MM");

    @Autowired
    public SeveranceApprovalDraftService(SeverancePaysRepository severancePaysRepository, EmployeeRepository employeeRepository, ApprovalFormCache approvalFormCache) {
        this.severancePaysRepository = severancePaysRepository;
        this.employeeRepository = employeeRepository;
        this.approvalFormCache = approvalFormCache;
    }


//    퇴직금 지급결의서 데이터 조회(미리보기)
    public ApprovalDraftResDto draft(UUID companyId, Long userId, Long sevId){

        SeverancePays sev = severancePaysRepository.findBySevIdAndCompany_CompanyId(sevId, companyId).orElseThrow(()-> new CustomException(ErrorCode.SEVERANCE_NOT_FOUND));

//        결재 가능 상태 검증(Confirmed만 상신 가능)
        if (sev.getSevStatus() != SevStatus.CONFIRMED){
            throw new CustomException(ErrorCode.SEVERANCE_STATUS_INVALID);
        }

        Employee drafter = employeeRepository.findById(userId).orElseThrow(()-> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));
        Employee target = sev.getEmployee();

        ApprovalFormCache.CachedForm form = approvalFormCache.get(companyId, ApprovalFormType.RETIREMENT);
        String htmlTemplate = form.formHtml();
        Map<String, String> dataMap = buildDataMap(sev, drafter, target);

        return ApprovalDraftResDto.builder()
                .type(ApprovalFormType.RETIREMENT)
                .ledgerId(sevId)
                .htmlTemplate(htmlTemplate)
                .dataMap(dataMap)
                .build();
    }

    private Map<String, String> buildDataMap(SeverancePays sev, Employee drafter, Employee target) {
        Map<String, String> m = new HashMap<>();

        // ── 헤더 ──
        m.put("drafterName", drafter.getEmpName());
        m.put("drafterDept", drafter.getDept() != null ? drafter.getDept().getDeptName() : "");
        m.put("draftDate",   LocalDate.now().format(YMD));
        m.put("docNo",       generateDocNo(sev));
        m.put("approvalLineHtml", "");  // 결재선 선택 후 프론트가 채움

        // ── 지급 기본 정보 ──
        m.put("payRequestDate", LocalDate.now().format(YMD));
        m.put("totalPayAmount", currency(sev.getNetAmount()));
        m.put("payHeadcount",   "1명");
        m.put("payDescription", String.format("%s님 %s 퇴직금 지급",
                sev.getEmpName(), sev.getRetirementType().name()));

        // ── 1. 퇴사자 인적사항 ──
        m.put("deptName",  sev.getDeptName() != null ? sev.getDeptName() : "");
        m.put("empNo",     target.getEmpNum() != null ? target.getEmpNum() : "");
        m.put("empName",   sev.getEmpName());
        m.put("jobTitle",  target.getTitle() != null ? target.getTitle().getTitleName() : "");
        m.put("grade",  sev.getGradeName() != null ? sev.getGradeName() : "");
        m.put("retirementType", sev.getRetirementType().name());
        m.put("joinDate",  sev.getHireDate().format(YMD));
        m.put("retireDate", sev.getResignDate().format(YMD));
        m.put("retirementPayDate", LocalDate.now().plusDays(14).format(YMD));  // 기본값
        m.put("settlementPeriod", sev.getHireDate().format(YMD) + " ~ " + sev.getResignDate().format(YMD));
        m.put("servicePeriod", formatServicePeriod(sev.getServiceDays()));
        m.put("settlementDays", String.valueOf(sev.getServiceDays()));

        // ── 2. 퇴직급여 산정내역 — 최근 3개월 급여 ──
        // 라벨/월별 데이터는 PayrollService에서 보조 조회 메서드 추가 필요 (TODO 1번)
        // 현재는 합계 위주로만 채움
        m.put("period1Label", "");
        m.put("period2Label", "");
        m.put("period3Label", "");
        m.put("period4Label", "합계");
        m.put("workDays1", "");
        m.put("workDays2", "");
        m.put("workDays3", "");
        m.put("workDays4", "");
        m.put("workDaysTotal", String.valueOf(sev.getLast3MonthDays()));
        m.put("salaryTotal1", "");
        m.put("salaryTotal2", "");
        m.put("salaryTotal3", "");
        m.put("salaryTotal4", "");
        m.put("salaryGrandTotal", currency(sev.getLast3MonthPay()));

        m.put("bonusTotal",       currency(sev.getLastYearBonus()));
        m.put("bonusAdded",       currency(calc3of12(sev.getLastYearBonus())));
        m.put("annualLeaveTotal", currency(sev.getAnnualLeaveForAvgWage()));
        m.put("annualLeaveAdded", currency(calc3of12(sev.getAnnualLeaveForAvgWage())));
        m.put("dailyAveragePay",  currency(sev.getAvgDailyWage().longValue()));

        // ── 3. 퇴직금 산정내역 ──
        m.put("retirementPayAmount", currency(sev.getSeveranceAmount()));
        m.put("incomeTax",      currency(sev.getTaxAmount()));
        m.put("localIncomeTax", currency(sev.getLocalIncomeTax()));
        m.put("paymentTotal",   currency(sev.getSeveranceAmount() + sev.getAnnualLeaveOnRetirement()));
        m.put("deductionTotal", currency(sev.getTaxAmount() + sev.getLocalIncomeTax()));
        m.put("netPayAmount",   currency(sev.getNetAmount()));

        return m;
    }



    // ── 헬퍼 ──

    private static String generateDocNo(SeverancePays sev) {
        return String.format("SEV-%s-%d",
                sev.getResignDate().format(DateTimeFormatter.ofPattern("yyyyMM")),
                sev.getSevId());
    }
}
