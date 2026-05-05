package com.peoplecore.pay.approval;

import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.pay.domain.SeverancePays;
import com.peoplecore.pay.enums.SevStatus;
import com.peoplecore.pay.repository.SeverancePaysRepository;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
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
    public ApprovalDraftResDto draft(UUID companyId, Long userId, List<Long> sevIds) {


    // 1. sevIds 일괄 조회 + 검증
    List<SeverancePays> sevs = severancePaysRepository
            .findAllBySevIdInAndCompany_CompanyId(sevIds, companyId);

        if (sevs.size() != sevIds.size()) {
            throw new CustomException(ErrorCode.SEVERANCE_NOT_FOUND);
        }
        for (SeverancePays s : sevs) {
            //        결재 가능 상태 검증(Confirmed만 상신 가능)
            if (s.getSevStatus() != SevStatus.CONFIRMED) {
                throw new CustomException(ErrorCode.SEVERANCE_STATUS_INVALID);
            }
            if (s.getApprovalDocId() != null) {
                throw new CustomException(ErrorCode.SEVERANCE_ALREADY_IN_APPROVAL);
            }
        }

        // 2. 기안자 조회
        Employee drafter = employeeRepository.findById(userId).orElseThrow(()-> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

        // 3. 양식 HTML 가져오기 + in-memory 빌드 (헤더 치환 + 행 주입)
        ApprovalFormCache.CachedForm form = approvalFormCache.get(companyId, ApprovalFormType.RETIREMENT);
        String renderedHtml = buildBatchHtml(form.formHtml(), drafter, sevs);

        // 4. dataMap 빈 맵 — 빌드 단계에서 모든 치환 완료. 프론트는 customHtmlTemplate 그대로 사용
        return ApprovalDraftResDto.builder()
                .type(ApprovalFormType.RETIREMENT)
                .ledgerId(null)
                .sevIds(sevIds)
                .htmlTemplate(renderedHtml)
                .dataMap(Map.of())
                .build();
    }


    // 다인 결의서 HTML 빌드 (헤더 텍스트 치환 + 사원별 <tr> 주입 + 합계).
    // 1명이든 N명이든 동일 경로.
    private String buildBatchHtml(String templateHtml, Employee drafter, List<SeverancePays> sevs) {
        Document doc = Jsoup.parse(templateHtml);
        doc.outputSettings().prettyPrint(false);   // fragment HTML 보존

        // 합계 계산
        long totalSev = sevs.stream().mapToLong(SeverancePays::getSeveranceAmount).sum();
        long totalTax = sevs.stream().mapToLong(s -> s.getTaxAmount() + s.getLocalIncomeTax()).sum();
        long totalNet = sevs.stream().mapToLong(SeverancePays::getNetAmount).sum();

        // 헤더/합계 텍스트 치환
        Map<String, String> m = new HashMap<>();
        m.put("drafterName", drafter.getEmpName());
        m.put("drafterDept", drafter.getDept() != null ? drafter.getDept().getDeptName() : "");
        m.put("draftDate",   LocalDate.now().format(YMD));
        m.put("docNo",       generateBatchDocNo(sevs));
        m.put("payRequestDate", LocalDate.now().format(YMD));
        m.put("totalPayAmount", currency(totalNet));
        m.put("payHeadcount",   sevs.size() + "명");
        m.put("payDescription", String.format("%d명 퇴직금 일괄 지급", sevs.size()));
        m.put("totalSeverance", currency(totalSev));
        m.put("totalTaxAmount", currency(totalTax));
        m.put("totalNetAmount", currency(totalNet));
        m.forEach((key, value) -> {
            Element el = doc.selectFirst("[data-key=" + key + "]");
            if (el != null) el.text(value);
        });

        // 사원별 <tr> 주입 (PayrollApprovalHtmlBuilder.injectPaymentRows 와 동일 패턴)
        Element tbody = doc.selectFirst("tbody[data-key=employeesRows]");
        if (tbody == null) {
            throw new CustomException(ErrorCode.APPROVAL_FORM_INVALID);
        }
        tbody.empty();
        int idx = 1;
        for (SeverancePays s : sevs) {
            Element tr = tbody.appendElement("tr");
            tr.appendElement("td").text(String.valueOf(idx++));
            tr.appendElement("td").text(nullSafe(s.getDeptName()));
            tr.appendElement("td").text(s.getEmpName());
            tr.appendElement("td").text(s.getRetirementType().name());
            tr.appendElement("td").text(s.getHireDate().format(YMD));
            tr.appendElement("td").text(s.getResignDate().format(YMD));
            tr.appendElement("td").text(formatServicePeriod(s.getServiceDays()));
            tr.appendElement("td").addClass("currency").text(currency(s.getSeveranceAmount()));
            tr.appendElement("td").addClass("currency")
                    .text(currency(s.getTaxAmount() + s.getLocalIncomeTax()));
            tr.appendElement("td").addClass("currency").text(currency(s.getNetAmount()));
        }

        return doc.outerHtml();
    }

    private String generateBatchDocNo(List<SeverancePays> sevs) {
        String yyyymm = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        return sevs.size() == 1
                ? String.format("SEV-%s-%d", yyyymm, sevs.get(0).getSevId())
                : String.format("SEV-%s-BATCH%d", yyyymm, sevs.get(0).getSevId());
    }

    private static String nullSafe(String s) { return s == null ? "" : s; }
}
