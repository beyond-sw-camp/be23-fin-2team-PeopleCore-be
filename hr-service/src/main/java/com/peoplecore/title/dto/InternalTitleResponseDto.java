package com.peoplecore.title.dto;

import com.peoplecore.title.domain.Title;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InternalTitleResponseDto {
    private Long titleId;
    private UUID companyId;
    private Long deptId;
    private String titleName;
    private String titleCode;

    public static InternalTitleResponseDto from(Title title) {
        return InternalTitleResponseDto.builder()
                .titleId(title.getTitleId())
                .companyId(title.getCompanyId())
                .deptId(title.getDeptId())
                .titleName(title.getTitleName())
                .titleCode(title.getTitleCode())
                .build();
    }
}
