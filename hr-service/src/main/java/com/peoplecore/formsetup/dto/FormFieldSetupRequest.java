package com.peoplecore.formsetup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FormFieldSetupRequest {
    @NotBlank(message = "필드키는 필수입니다")
    private String fieldKey;
    @NotBlank(message = "필드명은 필수입니다")
    private String label;
    @NotBlank(message = "필드섹션은 필수입니다")
    private String section;
    @NotNull(message = "입력방식 입력은 필수입니다")
    private String fieldType;
    @NotNull(message = "표시여부는 필수입니다")
    private Boolean visible;
    @NotNull(message = "필수여부는 필수입니다")
    private Boolean required;
    @NotNull(message = "순서는 필수입니다")
    private Integer sortOrder;
    private List<String> options; //셀렉트 시
    private String autoFillFrom; //자동입력선택
}
