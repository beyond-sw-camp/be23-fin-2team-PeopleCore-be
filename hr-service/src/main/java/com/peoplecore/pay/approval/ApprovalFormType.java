package com.peoplecore.pay.approval;


public enum ApprovalFormType {
    SALARY("PAYROLL_PAYMENT"),
    RETIREMENT("RETIREMENT_SEVERANCE");

    private final String formCode;

    ApprovalFormType(String formCode) {
        this.formCode = formCode;
    }

    public String getFormCode(){
        return formCode;
    }
}
