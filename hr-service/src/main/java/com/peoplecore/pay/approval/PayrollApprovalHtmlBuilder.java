package com.peoplecore.pay.approval;

import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.pay.domain.PayItems;
import com.peoplecore.pay.domain.PayrollDetails;
import com.peoplecore.pay.domain.PayrollRuns;
import com.peoplecore.pay.enums.PayItemType;
import com.peoplecore.pay.repository.PayItemsRepository;
import com.peoplecore.pay.repository.PayrollDetailsRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

// - `Jsoup.parse()` 로 양식 HTML을 DOM 트리로 파싱
// - `selectFirst("tbody.dynamic-payment-rows")` 로 마커 영역 식별
// - `tbody.empty()` 후 `tbody.append(...)` 로 동적 행 주입
// - 결과는 `doc.outerHtml()` 로 직렬화
// - 양식 자체는 변경되지 않음 (in-memory 작업)
// - `<details>` 표준 HTML로 사원 명단 접기 — JS 없이 브라우저가 처리

@Component
public class PayrollApprovalHtmlBuilder {

    private final ApprovalFormCache approvalFormCache;
    private final PayItemsRepository payItemsRepository;
    private final PayrollDetailsRepository payrollDetailsRepository;
    private final EmployeeRepository employeeRepository;

    @Autowired
    public PayrollApprovalHtmlBuilder(ApprovalFormCache approvalFormCache, PayItemsRepository payItemsRepository, PayrollDetailsRepository payrollDetailsRepository, EmployeeRepository employeeRepository) {
        this.approvalFormCache = approvalFormCache;
        this.payItemsRepository = payItemsRepository;
        this.payrollDetailsRepository = payrollDetailsRepository;
        this.employeeRepository = employeeRepository;
    }

    /**
     * 급여지급결의서 HTML 빌드 — 기존 양식 + 마커 영역 in-memory 교체.
     */
    public String buildSalaryHtml(PayrollRuns run, Employee drafter, Set<Long> confirmedEmpIds) {
        UUID companyId = run.getCompany().getCompanyId();

        // 1. 기존 양식 HTML 가져오기 (collab-service → MinIO 캐시)
        ApprovalFormCache.CachedForm form = approvalFormCache.get(companyId, ApprovalFormType.SALARY);
        Document doc = Jsoup.parse(form.formHtml());
        // 결의서는 fragment HTML이라 outputSettings에 prettyPrint(false) 설정해 원본 보존
        doc.outputSettings().prettyPrint(false);

        // 2. 노출할 PayItem 결정
        List<PayItems> paymentItems = resolveItemsToShow(
                companyId, run.getPayrollRunId(), confirmedEmpIds, PayItemType.PAYMENT);
        List<PayItems> deductionItems = resolveItemsToShow(
                companyId, run.getPayrollRunId(), confirmedEmpIds, PayItemType.DEDUCTION);

        // 3. 항목별 합계
        Map<String, ItemTotal> paymentTotals = aggregatePaymentTotals(
                run.getPayrollRunId(), confirmedEmpIds, paymentItems);
        Map<String, Long> deductionTotals = aggregateDeductionTotals(
                run.getPayrollRunId(), confirmedEmpIds, deductionItems);

        // 4. 부서별 요약 / 사원 명단
        List<DeptSummary> deptSummaries = aggregateByDepartment(
                run.getPayrollRunId(), confirmedEmpIds);
        List<EmpSummary> empSummaries = listEmployees(
                run.getPayrollRunId(), confirmedEmpIds);

        // 5. 헤더 영역 dataMap 채우기 (기존 data-key 방식 그대로)
        injectHeaderData(doc, run, drafter, confirmedEmpIds.size(), paymentTotals, deductionTotals);

        // 6. 마커 영역 교체 (in-memory)
        injectPaymentRows(doc, paymentItems, paymentTotals);
        injectDeductionRows(doc, deductionItems, deductionTotals);
        injectDepartmentSummary(doc, deptSummaries);
        injectEmployeeList(doc, empSummaries);

        return doc.outerHtml();
    }

    /**
     * 활성 + 데이터 있는 비활성 항목.
     */
    private List<PayItems> resolveItemsToShow(UUID companyId, Long runId,
                                              Set<Long> empIds, PayItemType type) {
        List<PayItems> active = payItemsRepository
                .findByCompany_CompanyIdAndPayItemTypeAndIsActiveOrderBySortOrder(
                        companyId, type, true);

        Set<Long> activeIds = active.stream()
                .map(PayItems::getPayItemId).collect(Collectors.toSet());

        Set<Long> usedIds = payrollDetailsRepository
                .findDistinctPayItemIdsByRunIdAndEmpIdInAndPayItemType(runId, empIds, type);

        Set<Long> inactiveButUsed = new HashSet<>(usedIds);
        inactiveButUsed.removeAll(activeIds);

        if (inactiveButUsed.isEmpty()) return active;

        List<PayItems> inactive = payItemsRepository.findByPayItemIdInAndCompany_CompanyId(
                new ArrayList<>(inactiveButUsed), companyId);
        inactive.sort(Comparator.comparing(PayItems::getSortOrder));

        List<PayItems> result = new ArrayList<>(active);
        result.addAll(inactive);
        return result;
    }

    /**
     * 마커 `tbody.dynamic-payment-rows`를 찾아 항목 행 + 소계 + 합계 채움.
     * 양식 스타일(class="tit"/"currency")과 contenteditable 속성을 그대로 따라야 함.
     */
    private void injectPaymentRows(Document doc,
                                   List<PayItems> items,
                                   Map<String, ItemTotal> totals) {
        Element tbody = doc.selectFirst("tbody.dynamic-payment-rows");
        if (tbody == null) {
            throw new IllegalStateException("양식에 dynamic-payment-rows 마커 없음");
        }
        tbody.empty();

        long subTaxable = 0L, subNonTaxable = 0L;
        for (PayItems item : items) {
            ItemTotal t = totals.getOrDefault(item.getPayItemName(), ItemTotal.ZERO);
            tbody.append(String.format(
                    "<tr>"
                            + "<td class='tit'>%s</td>"
                            + "<td class='currency' contenteditable='true'>%s</td>"
                            + "<td class='currency' contenteditable='true'>%s</td>"
                            + "</tr>",
                    escape(item.getPayItemName()), currency(t.taxable), currency(t.nonTaxable)));
            subTaxable += t.taxable;
            subNonTaxable += t.nonTaxable;
        }
        // 소계
        tbody.append(String.format(
                "<tr>"
                        + "<td class='tit'>소계</td>"
                        + "<td class='currency' contenteditable='true'>%s</td>"
                        + "<td class='currency' contenteditable='true'>%s</td>"
                        + "</tr>",
                currency(subTaxable), currency(subNonTaxable)));
        // 합계 (배경색 #bbb, 굵게)
        tbody.append(String.format(
                "<tr>"
                        + "<td class='tit' style='background:#bbb;'>합계</td>"
                        + "<td class='currency' colspan='2' style='font-weight:bold;' contenteditable='true'>%s</td>"
                        + "</tr>",
                currency(subTaxable + subNonTaxable)));
    }

    private void injectDeductionRows(Document doc,
                                     List<PayItems> items,
                                     Map<String, Long> totals) {
        Element tbody = doc.selectFirst("tbody.dynamic-deduction-rows");
        if (tbody == null) {
            throw new IllegalStateException("양식에 dynamic-deduction-rows 마커 없음");
        }
        tbody.empty();

        long sum = 0L;
        for (PayItems item : items) {
            long amount = totals.getOrDefault(item.getPayItemName(), 0L);
            tbody.append(String.format(
                    "<tr>"
                            + "<td class='tit'>%s</td>"
                            + "<td class='currency' contenteditable='true'>%s</td>"
                            + "</tr>",
                    escape(item.getPayItemName()), currency(amount)));
            sum += amount;
        }
        tbody.append(String.format(
                "<tr>"
                        + "<td class='tit' style='background:#bbb;'>합계</td>"
                        + "<td class='currency' style='font-weight:bold;' contenteditable='true'>%s</td>"
                        + "</tr>",
                currency(sum)));
    }

    private void injectDepartmentSummary(Document doc, List<DeptSummary> deptSummaries) {
        Element tbody = doc.selectFirst("tbody.dynamic-dept-summary");
        if (tbody == null) {
            throw new IllegalStateException("양식에 dynamic-dept-summary 마커 없음");
        }
        tbody.empty();
        for (DeptSummary d : deptSummaries) {
            tbody.append(String.format(
                    "<tr>"
                            + "<td class='tit'>%s</td>"
                            + "<td class='currency'>%d명</td>"
                            + "<td class='currency'>%s</td>"
                            + "<td class='currency'>%s</td>"
                            + "<td class='currency'>%s</td>"
                            + "</tr>",
                    escape(d.deptName), d.empCount,
                    currency(d.totalPay), currency(d.totalDeduction), currency(d.netPay)));
        }
    }

    private void injectEmployeeList(Document doc, List<EmpSummary> empSummaries) {
        Element details = doc.selectFirst("details.dynamic-employee-list");
        if (details == null) {
            throw new IllegalStateException("양식에 dynamic-employee-list 마커 없음");
        }
        // summary 텍스트 갱신 (사원수 표시)
        Element summary = details.selectFirst("summary");
        if (summary != null) {
            summary.text("▼ 사원 명단 (총 " + empSummaries.size() + "명)");
        }
        // 사원 행은 두번째 <tbody> (헤더 행은 첫번째 <tbody>에 정적으로 존재)
        Elements tbodies = details.select("table tbody");
        if (tbodies.size() < 2) return;
        Element tbody = tbodies.get(1);
        tbody.empty();
        for (EmpSummary e : empSummaries) {
            tbody.append(String.format(
                    "<tr>"
                            + "<td>%s</td><td>%s</td><td>%s</td>"
                            + "<td class='currency'>%s</td>"
                            + "</tr>",
                    escape(e.deptName), escape(e.empName), escape(e.gradeName), currency(e.totalPay)));
        }
    }


    /**
     * 항목별 합계 (과세/비과세 분리) — 기존 summarizeByPayItemForEmployees 재사용.
     * 분리 기준: PayItem.isTaxable boolean. taxExemptLimit 한도 분할은 산정 시점에
     * PayrollDetails에 이미 반영되어 있으므로 이 단계에서 추가 처리 불필요.
     */
    private Map<String, ItemTotal> aggregatePaymentTotals(
            Long runId, Set<Long> empIds, List<PayItems> items) {
        List<PayrollItemSummaryDto> summary = payrollDetailsRepository
                .summarizeByPayItemForEmployees(runId, PayItemType.PAYMENT, empIds);

        Map<String, Long> taxable = new HashMap<>();
        Map<String, Long> nonTaxable = new HashMap<>();
        for (PayrollItemSummaryDto row : summary) {
            if (Boolean.TRUE.equals(row.isTaxable())) {
                taxable.merge(row.payItemName(), row.totalAmount(), Long::sum);
            } else {
                nonTaxable.merge(row.payItemName(), row.totalAmount(), Long::sum);
            }
        }

        Map<String, ItemTotal> result = new LinkedHashMap<>();
        for (PayItems item : items) {
            String name = item.getPayItemName();
            result.put(name, new ItemTotal(
                    taxable.getOrDefault(name, 0L),
                    nonTaxable.getOrDefault(name, 0L)));
        }
        return result;
    }

    /**
     * 공제 항목별 합계 — 단순 합계.
     */
    private Map<String, Long> aggregateDeductionTotals(
            Long runId, Set<Long> empIds, List<PayItems> items) {
        List<PayrollItemSummaryDto> summary = payrollDetailsRepository
                .summarizeByPayItemForEmployees(runId, PayItemType.DEDUCTION, empIds);

        Map<String, Long> total = new HashMap<>();
        for (PayrollItemSummaryDto row : summary) {
            total.merge(row.payItemName(), row.totalAmount(), Long::sum);
        }

        Map<String, Long> result = new LinkedHashMap<>();
        for (PayItems item : items) {
            result.put(item.getPayItemName(), total.getOrDefault(item.getPayItemName(), 0L));
        }
        return result;
    }

    /**
     * 부서별 요약 — 부서명 / 인원 / 지급합계 / 공제합계 / 실지급액.
     */
    private List<DeptSummary> aggregateByDepartment(Long runId, Set<Long> empIds) {
        List<Employee> emps = employeeRepository.findAllById(empIds);
        Map<Long, String> empToDept = new HashMap<>();
        for (Employee e : emps) {
            empToDept.put(e.getEmpId(),
                    e.getDept() != null ? e.getDept().getDeptName() : "미배정");
        }

        List<PayrollDetails> details = payrollDetailsRepository
                .findByPayrollRuns_PayrollRunIdAndEmployee_EmpIdIn(runId, empIds);

        Map<String, long[]> deptAgg = new LinkedHashMap<>();   // [pay, deduction]
        Map<String, Set<Long>> deptEmps = new HashMap<>();
        for (PayrollDetails pd : details) {
            Long empId = pd.getEmployee().getEmpId();
            String deptName = empToDept.getOrDefault(empId, "미배정");
            deptEmps.computeIfAbsent(deptName, k -> new HashSet<>()).add(empId);
            long[] agg = deptAgg.computeIfAbsent(deptName, k -> new long[2]);
            if (pd.getPayItemType() == PayItemType.PAYMENT) {
                agg[0] += pd.getAmount();
            } else if (pd.getPayItemType() == PayItemType.DEDUCTION) {
                agg[1] += pd.getAmount();
            }
        }

        List<DeptSummary> result = new ArrayList<>();
        for (Map.Entry<String, long[]> e : deptAgg.entrySet()) {
            long[] agg = e.getValue();
            int empCount = deptEmps.get(e.getKey()).size();
            result.add(new DeptSummary(e.getKey(), empCount, agg[0], agg[1], agg[0] - agg[1]));
        }
        return result;
    }

    /**
     * 사원 명단 — 부서/사원명/직급/총지급액. 부서명 → 사원명 가나다순.
     */
    private List<EmpSummary> listEmployees(Long runId, Set<Long> empIds) {
        List<Employee> emps = employeeRepository.findAllById(empIds);

        List<PayrollDetails> details = payrollDetailsRepository
                .findByPayrollRuns_PayrollRunIdAndEmployee_EmpIdIn(runId, empIds);
        Map<Long, Long> empTotalPay = new HashMap<>();
        for (PayrollDetails pd : details) {
            if (pd.getPayItemType() != PayItemType.PAYMENT) continue;
            empTotalPay.merge(pd.getEmployee().getEmpId(), pd.getAmount(), Long::sum);
        }

        return emps.stream()
                .sorted(Comparator
                        .comparing((Employee e) -> e.getDept() != null ? e.getDept().getDeptName() : "")
                        .thenComparing(Employee::getEmpName))
                .map(e -> new EmpSummary(
                        e.getDept() != null ? e.getDept().getDeptName() : "미배정",
                        e.getEmpName(),
                        e.getGrade() != null ? e.getGrade().getGradeName() : "",
                        empTotalPay.getOrDefault(e.getEmpId(), 0L)))
                .toList();
    }
    /**
     * 헤더 영역 (data-key 기반)을 기존 양식의 키 그대로 채움.
     * 양식의 실제 data-key:
     *   drafterName, drafterDept, draftDate, docNo, approvalLineHtml
     *   payMonth, payScheduledDate, payHeadcount, totalPayAmount
     */
    private void injectHeaderData(Document doc, PayrollRuns run, Employee drafter,
                                  int headcount,
                                  Map<String, ItemTotal> paymentTotals,
                                  Map<String, Long> deductionTotals) {
        Map<String, String> map = new HashMap<>();
        map.put("docNo", String.format("PAY-%s-%d", run.getPayYearMonth(), run.getPayrollRunId()));
        map.put("draftDate", LocalDate.now().toString());
        map.put("drafterName", drafter.getEmpName());
        map.put("drafterDept", drafter.getDept() != null ? drafter.getDept().getDeptName() : "");
        map.put("payMonth", run.getPayYearMonth());
        map.put("payScheduledDate", run.getPayDate() != null ? run.getPayDate().toString() : "");
        map.put("payHeadcount", headcount + "명");

        long totalPay = paymentTotals.values().stream()
                .mapToLong(t -> t.taxable + t.nonTaxable).sum();
        long totalDed = deductionTotals.values().stream().mapToLong(Long::longValue).sum();
        map.put("totalPayAmount", currency(totalPay - totalDed));
        // approvalLineHtml 은 프론트에서 결재선 선택 후 채우므로 백엔드는 비워둠

        for (Map.Entry<String, String> e : map.entrySet()) {
            for (Element el : doc.select("[data-key=\"" + e.getKey() + "\"]")) {
                el.text(e.getValue());
            }
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String currency(long v) {
        return String.format("%,d", v);
    }

    // 내부 DTO
    private record ItemTotal(long taxable, long nonTaxable) {
        static final ItemTotal ZERO = new ItemTotal(0, 0);
    }
    private record DeptSummary(String deptName, int empCount, long totalPay,
                               long totalDeduction, long netPay) {}
    private record EmpSummary(String deptName, String empName, String gradeName, long totalPay) {}

}
