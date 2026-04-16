package com.peoplecore.pay.enums;

import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;

//회사 퇴직 제도 설정용
public enum PensionType {
    severance, DB, DC, DB_DC;

    //    회사 퇴직제도 -> 사원 퇴직제도 유형 변환
    /* 단일 제도 (severance/DB/DC) : 직접 매핑
     *  DB_DC(병행) : 사원 개인 설정이 필요하므로 변환 불가-> 예외 */
    public RetirementType toRetirementType() {
        return switch (this) {
            case severance -> RetirementType.severance;
            case DB -> RetirementType.DB;
            case DC -> RetirementType.DC;
            case DB_DC -> throw new CustomException(ErrorCode.EMPLOYEE_RETIREMENT_TYPE_NOT_SET);
        };
    }
}
