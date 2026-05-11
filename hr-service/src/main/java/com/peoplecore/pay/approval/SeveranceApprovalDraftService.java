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
        long totalDeposited = sevs.stream().mapToLong(this::dcDepositedAmount).sum();
        long totalPayable = sevs.stream().mapToLong(this::payableSeveranceAmount).sum();
        long totalTax = sevs.stream().mapToLong(this::taxAmount).sum();
        long totalNet = sevs.stream().mapToLong(this::netPayAmount).sum();

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
        m.put("totalDcDeposited", currency(totalDeposited));
        m.put("totalDepositedAmount", currency(totalDeposited));
        m.put("totalDcDiffAmount", currency(totalPayable));
        m.put("totalPayableAmount", currency(totalPayable));
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
        ensureEmployeeTableColumns(tbody);
        tbody.empty();
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

        return doc.outerHtml();
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
                .addClass("currency")
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
        String[] widths = {"40px", "72px", "72px", "48px", "100px", "100px", "110px", "110px", "110px", "110px", "55px", "110px"};
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
