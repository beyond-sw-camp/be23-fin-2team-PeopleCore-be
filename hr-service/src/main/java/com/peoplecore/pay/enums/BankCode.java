package com.peoplecore.pay.enums;

import lombok.Getter;

@Getter
public enum BankCode {
//    대량이체 파일 관련해서 사용

    KB("004", "KB국민은행"),
    SHINHAN("088", "신한은행"),
    WOORI("020", "우리은행"),
    HANA("081", "하나은행"),
    NH("011", "NH농협은행"),
    IBK("003", "IBK기업은행");

    private final String code;
    private final String bankName;


    BankCode(String code, String bankName) {
        this.code = code;
        this.bankName = bankName;
    }

    public static BankCode fromCode(String code){
        for(BankCode b : values()){
            if (b.code.equals(code)) return b;
        }
        throw new IllegalStateException("지원하지 않는 은행코드: " + code);
}
}
