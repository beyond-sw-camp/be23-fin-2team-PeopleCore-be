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
        long totalDeposited = sevs.stream().mapToLong(this::dcDepositedAmount).sum();
        long totalPayable = sevs.stream().mapToLong(this::payableSeveranceAmount).sum();
        long totalTax = sevs.stream().mapToLong(this::taxAmount).sum();
        long totalNet = sevs.stream().mapToLong(this::netPayAmount).sum();

        // 3. 헤더 영역 data-key 치환
        injectHeaderData(doc, drafter, sevs, totalSev, totalDeposited, totalPayable, totalTax, totalNet);

        // 4. 사원별 행 마커 교체
        injectEmployeeRows(doc, sevs);

        return doc.outerHtml();
    }

    /**
     * 헤더 + 합계 영역의 data-key 텍스트 치환.
     * (PayrollApprovalHtmlBuilder.injectHeaderData 와 동일 패턴)
     */
    private void injectHeaderData(Document doc, Employee drafter, List<SeverancePays> sevs,
                                  long totalSev, long totalDeposited, long totalPayable,
                                  long totalTax, long totalNet) {
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
        m.put("totalDcDeposited", currency(totalDeposited));
        m.put("totalDepositedAmount", currency(totalDeposited));
        m.put("totalDcDiffAmount", currency(totalPayable));
        m.put("totalPayableAmount", currency(totalPayable));
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
        ensureEmployeeTableColumns(tbody);
        tbody.empty();   // PayrollApprovalHtmlBuilder.injectPaymentRows 와 동일 패턴

        int idx = 1;
        for (SeverancePays s : sevs) {
            Element tr = tbody.appendElement("tr");
            appendCenterCell(tr, String.valueOf(idx++));
            appendCenterCell(tr, nullSafe(s.getDeptName()));
            appendCenterCell(tr, s.getEmpName());
            appendCenterCell(tr, s.getRetirementType().name());
            appendCenterCell(tr, s.getHireDate().format(YMD));
            appendCenterCell(tr, s.getResignDate().format(YMD));
            appendCenterCell(tr, formatServicePeriod(s.getServiceDays()));
            appendAmountCell(tr, s.getSeveranceAmount());
            appendAmountCell(tr, dcDepositedAmount(s));
            appendAmountCell(tr, payableSeveranceAmount(s));
            appendAmountCell(tr, taxAmount(s));
            appendAmountCell(tr, netPayAmount(s));
        }
        injectEmployeeTotalRow(tbody, sevs);
    }

    private void ensureEmployeeTableColumns(Element tbody) {
        Element table = tbody.parent();
        if (table == null || !"table".equals(table.tagName())) {
            return;
        }
        table.attr("style", appendStyle(table.attr("style"), "width:1141px;min-width:1141px;max-width:1141px;table-layout:fixed;font-size:11px;"));
        ensureColumnGroup(table);
        Element headerRow = table.selectFirst("thead tr");
        if (headerRow == null) {
            return;
        }

        if (!table.text().contains("기적립액") && !table.text().contains("기지급액") && headerRow.childrenSize() >= 8) {
            Element severanceHeader = headerRow.child(7);
            severanceHeader.after("<th>기적립액</th><th>차액</th>");
        }
        applyColumnStyles(headerRow);

        Element tfoot = table.selectFirst("tfoot");
        if (tfoot != null) {
            tfoot.remove();
        }
    }

    private void appendCenterCell(Element tr, String text) {
        tr.appendElement("td")
                .attr("style", "white-space:nowrap;text-align:center;padding:6px 4px;")
                .text(text);
    }

    private void appendAmountCell(Element tr, Long amount) {
        tr.appendElement("td")
                .addClass("right")
                .attr("style", "white-space:nowrap;text-align:right;padding:6px 4px;")
                .text(currency(amount));
    }

    private void injectEmployeeTotalRow(Element tbody, List<SeverancePays> sevs) {
        long totalSev = sevs.stream().mapToLong(SeverancePays::getSeveranceAmount).sum();
        long totalDeposited = sevs.stream().mapToLong(this::dcDepositedAmount).sum();
        long totalPayable = sevs.stream().mapToLong(this::payableSeveranceAmount).sum();
        long totalTax = sevs.stream().mapToLong(this::taxAmount).sum();
        long totalNet = sevs.stream().mapToLong(this::netPayAmount).sum();

        Element tr = tbody.appendElement("tr");
        tr.attr("style", "font-weight:bold;background:#fafafa;");
        tr.appendElement("td")
                .attr("colspan", "7")
                .attr("style", "text-align:right;padding:6px 4px;")
                .text("합계");
        appendAmountCell(tr, totalSev);
        appendAmountCell(tr, totalDeposited);
        appendAmountCell(tr, totalPayable);
        appendAmountCell(tr, totalTax);
        appendAmountCell(tr, totalNet);
    }

    private void applyColumnStyles(Element headerRow) {
        String[] widths = {"44px", "72px", "72px", "48px", "100px", "100px", "120px", "125px", "120px", "135px", "55px", "150px"};
        for (int i = 0; i < headerRow.childrenSize() && i < widths.length; i++) {
            Element cell = headerRow.child(i);
            cell.attr("style", appendStyle(cell.attr("style"), "width:" + widths[i] + ";white-space:nowrap;text-align:center;padding:6px 4px;"));
        }
    }

    private void ensureColumnGroup(Element table) {
        Element oldColgroup = table.selectFirst("colgroup");
        if (oldColgroup != null) {
            oldColgroup.remove();
        }
        String[] widths = {"44px", "72px", "72px", "48px", "100px", "100px", "120px", "125px", "120px", "135px", "55px", "150px"};
        Element colgroup = table.prependElement("colgroup");
        for (String width : widths) {
            colgroup.appendElement("col").attr("style", "width:" + width + ";");
        }
    }

    private String appendStyle(String current, String addition) {
        return current == null || current.isBlank() ? addition : current + ";" + addition;
    }

    private String generateBatchDocNo(List<SeverancePays> sevs) {
        String yyyymm = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        return sevs.size() == 1
                ? String.format("SEV-%s-%d", yyyymm, sevs.get(0).getSevId())
                : String.format("SEV-%s-BATCH%d", yyyymm, sevs.get(0).getSevId());
    }

    private static String nullSafe(String s) { return s == null ? "" : s; }

    private boolean isDc(SeverancePays s) {
        return s.getRetirementType() != null && "DC".equals(s.getRetirementType().name());
    }

    private long dcDepositedAmount(SeverancePays s) {
        return isDc(s) ? nz(s.getDcDepositedTotal()) : 0L;
    }

    private long payableSeveranceAmount(SeverancePays s) {
        return isDc(s) ? nz(s.getDcDiffAmount()) : nz(s.getSeveranceAmount());
    }

    private long taxAmount(SeverancePays s) {
        return nz(s.getTaxAmount()) + nz(s.getLocalIncomeTax());
    }

    private long netPayAmount(SeverancePays s) {
        return payableSeveranceAmount(s) - taxAmount(s) + nz(s.getAnnualLeaveOnRetirement());
    }

    private long nz(Long value) {
        return value == null ? 0L : value;
    }

}
