package com.peoplecore.pay.approval;

public enum ApprovalFormType {
    SALARY("payroll-payment-approval.html", "PAYROLL_PAYMENT"),
    RETIREMENT("retirement-severance-approval.html", "RETIREMENT_SEVERANCE");

    private final String templateFileName;
    private final String formCode;

    ApprovalFormType(String templateFileName, String formCode) {
        this.templateFileName = templateFileName;
        this.formCode = formCode;
    }

    public String getTemplateFileName() {
        return templateFileName;
    }
    public String getFormCode(){
        return formCode;
    }
}
