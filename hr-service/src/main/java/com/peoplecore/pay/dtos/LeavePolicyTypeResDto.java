package com.peoplecore.pay.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeavePolicyTypeResDto {
//    프론트에서 탭을 분기하기위한 정책타입 응답

    private String policyBaseType;      // FISCAL 또는 HIRE
    private String fiscalYearStart;     //회계년도 시작일 (mm-dd), HIRE이면 null

}
