package com.peoplecore.pay.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApprovedOvertimeResDto {

//        월간 합계
        private Long totalExtendedMinutes;  //연장근로 합계
        private Long totalNightMinutes;     //야간근로 합계
        private Long totalHolidayMinutes;   //휴일근로 합계

        private Long extendedPay;       //연장수당
        private Long nightPay;          //야간수당
        private Long holidayPay;        //휴일수당
        private Long totalAmount;       //수당 합계 금액

        private boolean applied;        //이미 급여대장에 적용됐는지 여부

//        일별 상세
         private List<DailyOvertimeDto> dailyItems;

        @Data
        @AllArgsConstructor
        @Builder
        @NoArgsConstructor
        public static class DailyOvertimeDto{
             private LocalDate workDate;
             private Long recognizedExtendedMinutes; //연장
             private Long recognizedNightMinutes;    //야간
             private Long recognizedHolidayMinutes;  //휴일
             private Long actualWorkMinutes;         //실근무
    }
}
