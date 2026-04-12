package com.peoplecore.hrorder.dto;

import com.peoplecore.hrorder.domain.HrOrder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class HrOrderListReqDto {
    private Long orderId;
    private Long empId;
    private String empNum;
    private String empName;
    private String orderType; //발령유형 (PROMOTION/TRANSFER/TITLE_CHANGE)
    private String effectiveDate;
    private String status;  // 상태 (PENDING:승인대기 / CONFIRMED:승인 / REJECTED:반려 / APPLIED:반영완료)
    private boolean isNotified; //통보여부
    private String createAt; //등록일

    public static HrOrderListReqDto fromEntity(HrOrder order) {
        return HrOrderListReqDto.builder()
                .orderId(order.getOrderId())
                .empId(order.getEmployee().getEmpId())
                .empNum(order.getEmployee().getEmpNum())
                .empName(order.getEmployee().getEmpName())
                .orderType(order.getOrderType().name())             // enum -> 문자열
                .effectiveDate(order.getEffectiveDate().toString()) // LocalDate -> 문자열
                .status(order.getStatus().name())                   // enum -> 문자열
                .isNotified(order.getIsNotified())                  // 통보 여부
                .createAt(order.getCreatedAt().toString())        // LocalDateTime -> 문자열
                .build();
    }

}
