package com.peoplecore.title.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TitleCreateRequest {
    @NotBlank
    private String titleName;

    private String titleCode;
}
