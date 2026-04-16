package com.peoplecore.pay.enums;

import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.pay.domain.LeaveAllowance;
import org.springframework.cglib.core.Local;

import java.time.LocalDate;
import java.time.MonthDay;

public enum AllowanceType { //연차 수당구분
    //회계년도 기준
    FISCAL_YEAR{
        @Override
        public LocalDate resolveExpiredDate(LeaveAllowance la, String fiscalYearStart){
            if(fiscalYearStart == null){
//                해당연도 12/31에 소멸
                return LocalDate.of(la.getYear(), 12, 31);
            }
//            fiscalYearStart = "mm-dd"
            MonthDay md = MonthDay.parse("--" + fiscalYearStart);
//            소멸일 : year의 회계연도 시작일 전날
            return LocalDate.of(la.getYear()+1, md.getMonth(), md.getDayOfMonth()).minusDays(1);
        }
    },
    //입사일 기준 - 입사 기념일 전날(-1)에 소멸
    ANNIVERSARY{
        @Override
        public LocalDate resolveExpiredDate(LeaveAllowance la, String fiscalYearStart){
            if (la.getEmployee() == null || la.getEmployee().getEmpHireDate() == null){
                throw new CustomException(ErrorCode.EMPLOYEE_HIRE_DATE_NOT_FOUND);
            }
            LocalDate hireDate = la.getEmployee().getEmpHireDate();
//            year의 입사기념일 전날 = 해당 회차 연차의 소멸일
            return LocalDate.of(la.getYear(), hireDate.getMonth(), hireDate.getDayOfMonth()).minusDays(1    );
        }
    },
    //퇴직자 정산 - resignDate : 소멸일
    RESIGNED{
        @Override
        public LocalDate resolveExpiredDate(LeaveAllowance la, String fiscalYearStart){
            return la.getResignDate();
        }
    };

//    연차수당 소멸 일자 계산
    public abstract LocalDate resolveExpiredDate(LeaveAllowance la, String fiscalYearStart);
}
