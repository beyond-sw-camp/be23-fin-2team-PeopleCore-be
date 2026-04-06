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
    @NotBlank
    private String fieldKey;
    @NotBlank
    private String label;
    @NotBlank
    private String section;
    @NotNull
    private String fieldType;
    @NotNull
    private Boolean visible;
    @NotNull
    private Boolean required;
    @NotNull
    private Integer sortOrder;
    private List<String> options;
    private String linkedSource;
}
