package com.peoplecore.pay.approval;

import com.peoplecore.dtos.ApprovalLineDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApprovalSubmitReqDto {

    @NotNull(message = "결의서 유형은 필수입니다")
    private ApprovalFormType type;

    @NotNull(message = "ledgerId는 필수입니다")
    private Long ledgerId;          // SALARY: payrollRunId, RETIREMENT: sevId

    @NotBlank(message = "htmlContent는 필수입니다")
    private String htmlContent;     // injectApprovalData 결과 + 사용자 수정 반영된 최종 HTML

    @NotNull(message = "결재선은 필수입니다")
    private List<ApprovalLineDto> approvalLine;

}
