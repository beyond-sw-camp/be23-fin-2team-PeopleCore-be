package com.peoplecore.pay.approval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)   // collab 이 필드 추가해도 깨지지 않게
public class FormDetailResDto {
    private Long formId;
    private String formCode;
    private String formName;
    private String formHtml;
    private Integer formVersion;
    private Integer formRetentionYear;
    private Boolean formPreApprovalYn;
    private Boolean isProtected;
}
