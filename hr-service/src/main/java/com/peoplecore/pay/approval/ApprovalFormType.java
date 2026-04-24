package com.peoplecore.pay.approval;


public enum ApprovalFormType {
    SALARY("PAYROLL_RESOLUTION"),
    RETIREMENT("SEVERANCE_RESOLUTION");

    private final String formCode;

    ApprovalFormType(String formCode) {
        this.formCode = formCode;
    }

    public String getFormCode(){
        return formCode;
    }
}
