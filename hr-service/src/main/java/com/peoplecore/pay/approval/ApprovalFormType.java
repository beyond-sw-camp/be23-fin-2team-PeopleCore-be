package com.peoplecore.pay.approval;

import org.springframework.beans.factory.annotation.Autowired;

public enum ApprovalFormType {
    SALARY("급여지급결의서.html", "PAYROLL_PAYMENT"),
    RETIREMENT("퇴직급여지급결의서.html", "RETIREMENT_SEVERANCE");

    private final String templateFileName;
    private final String formCode;

    @Autowired
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
