package com.peoplecore.pay.approval;

import com.peoplecore.employee.domain.Employee;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.pay.domain.SeverancePays;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.peoplecore.pay.approval.ApprovalFormatter.currency;
import static com.peoplecore.pay.approval.ApprovalFormatter.formatServicePeriod;

//퇴직급여지급결의서 HTML 빌드 — 기존 양식 + 다인 행 마커 영역 in-memory 교체.
// Jsoup.parse() 로 양식 HTML 을 DOM 트리로 파싱
// selectFirst("tbody[data-key=employeesRows]") 마커 영역에 사원별 <tr> 직접 주입
// 결과는 doc.outerHtml() 로 직렬화 (1명이든 N명이든 동일 코드 경로)
@Component
public class SeveranceApprovalHtmlBuilder {

    private final ApprovalFormCache approvalFormCache;

    private static final DateTimeFormatter YMD = DateTimeFormatter.ISO_LOCAL_DATE;

    @Autowired
    public SeveranceApprovalHtmlBuilder(ApprovalFormCache approvalFormCache) {
        this.approvalFormCache = approvalFormCache;
    }

    /**
     * 다인 퇴직금 결의서 HTML 빌드.
     * @param companyId 회사 ID
     * @param drafter   기안자
     * @param sevs      결재 대상 SeverancePays 리스트 (1개 이상)
     * @return 헤더/행/합계 모두 채워진 완성 HTML
     */
    public String buildBatchHtml(UUID companyId, Employee drafter, List<SeverancePays> sevs) {
        // 1. 양식 HTML 가져오기
        ApprovalFormCache.CachedForm form = approvalFormCache.get(companyId, ApprovalFormType.RETIREMENT);
        Document doc = Jsoup.parse(form.formHtml());
        doc.outputSettings().prettyPrint(false);   // fragment HTML 보존

        // 2. 합계 계산
        long totalSev = sevs.stream().mapToLong(SeverancePays::getSeveranceAmount).sum();
        long totalTax = sevs.stream().mapToLong(s -> s.getTaxAmount() + s.getLocalIncomeTax()).sum();
        long totalNet = sevs.stream().mapToLong(SeverancePays::getNetAmount).sum();

        // 3. 헤더 영역 data-key 치환
        injectHeaderData(doc, drafter, sevs, totalSev, totalTax, totalNet);

        // 4. 사원별 행 마커 교체
        injectEmployeeRows(doc, sevs);

        return doc.outerHtml();
    }

    /**
     * 헤더 + 합계 영역의 data-key 텍스트 치환.
     * (PayrollApprovalHtmlBuilder.injectHeaderData 와 동일 패턴)
     */
    private void injectHeaderData(Document doc, Employee drafter, List<SeverancePays> sevs,
                                  long totalSev, long totalTax, long totalNet) {
        Map<String, String> m = new HashMap<>();

        // 헤더
        m.put("drafterName", drafter.getEmpName());
        m.put("drafterDept", drafter.getDept() != null ? drafter.getDept().getDeptName() : "");
        m.put("draftDate",   LocalDate.now().format(YMD));
        m.put("docNo",       generateBatchDocNo(sevs));

        // 지급 합계
        m.put("payRequestDate", LocalDate.now().format(YMD));
        m.put("totalPayAmount", currency(totalNet));
        m.put("payHeadcount",   sevs.size() + "명");
        m.put("payDescription", String.format("%d명 퇴직금 일괄 지급", sevs.size()));

        // 합계행 (tfoot)
        m.put("totalSeverance", currency(totalSev));
        m.put("totalTaxAmount", currency(totalTax));
        m.put("totalNetAmount", currency(totalNet));

        // data-key 치환
        m.forEach((key, value) -> {
            Element el = doc.selectFirst("[data-key=" + key + "]");
            if (el != null) el.text(value);
        });
    }

    /**
     * <tbody data-key="employeesRows"> 영역에 사원별 <tr> 직접 주입.
     * 기존 자식이 있으면 비우고 새로 채움.
     */
    private void injectEmployeeRows(Document doc, List<SeverancePays> sevs) {
        Element tbody = doc.selectFirst("tbody[data-key=employeesRows]");
        if (tbody == null) {
            throw new CustomException(ErrorCode.APPROVAL_FORM_INVALID);
        }
        tbody.empty();   // PayrollApprovalHtmlBuilder.injectPaymentRows 와 동일 패턴

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
            tr.appendElement("td").addClass("right").text(currency(s.getSeveranceAmount()));
            tr.appendElement("td").addClass("right")
                    .text(currency(s.getTaxAmount() + s.getLocalIncomeTax()));
            tr.appendElement("td").addClass("right").text(currency(s.getNetAmount()));
        }
    }

    private String generateBatchDocNo(List<SeverancePays> sevs) {
        String yyyymm = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        return sevs.size() == 1
                ? String.format("SEV-%s-%d", yyyymm, sevs.get(0).getSevId())
                : String.format("SEV-%s-BATCH%d", yyyymm, sevs.get(0).getSevId());
    }

    private static String nullSafe(String s) { return s == null ? "" : s; }

}
