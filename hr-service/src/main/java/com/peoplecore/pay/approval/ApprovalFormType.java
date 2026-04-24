package com.peoplecore.pay.approval;


public enum ApprovalFormType {
    SALARY("PAYROLL_RESOLUTION"),
    RETIREMENT("SEVERANCE_RESOLUTION");
//    SALARY("payroll-payment-approval.html", "PAYROLL_PAYMENT"),
//    RETIREMENT("retirement-severance-approval.html", "RETIREMENT_SEVERANCE");

    private final String formCode;

    ApprovalFormType(String formCode) {
        this.formCode = formCode;
    }

    public String getFormCode(){
        return formCode;
    }
}
