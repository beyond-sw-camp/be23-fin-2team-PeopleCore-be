package com.peoplecore.event;

import lombok.Getter;

import java.util.UUID;

@Getter
public class PayrollPaidEvent {

    private final UUID companyId;
    private final String payYearMonth;
    private final Long payrollRunId;

    public PayrollPaidEvent(UUID companyId, String payYearMonth, Long payrollRunId) {
        this.companyId = companyId;
        this.payYearMonth = payYearMonth;
        this.payrollRunId = payrollRunId;
    }
}
