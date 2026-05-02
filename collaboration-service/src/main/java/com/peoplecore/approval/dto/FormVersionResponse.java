package com.peoplecore.approval.dto;

import com.peoplecore.approval.entity.ApprovalForm;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FormVersionResponse {
    /** 양식 row id */
    private Long formId;
    /** 양식 명 (해당 버전 시점) */
    private String formName;
    /** 양식 버전 번호 */
    private Integer formVersion;
    /** 현재 활성 버전 여부 */
    private Boolean isCurrent;
    /** 양식 자체 활성 여부 (삭제된 양식이면 false) */
    private Boolean isActive;
    /** 작성자/수정자 사원 id (해당 버전 생성자) */
    private Long empId;
    /** 해당 버전 row INSERT 시점 */
    private LocalDateTime createdAt;
    /** 마지막 변경(롤백 활성화 등) 시점 */
    private LocalDateTime updatedAt;

    public static FormVersionResponse from(ApprovalForm form) {
        return FormVersionResponse.builder()
                .formId(form.getFormId())
                .formName(form.getFormName())
                .formVersion(form.getFormVersion())
                .isCurrent(form.getIsCurrent())
                .isActive(form.getIsActive())
                .empId(form.getEmpId())
                .createdAt(form.getCreatedAt())
                .updatedAt(form.getUpdatedAt())
                .build();
    }
}
