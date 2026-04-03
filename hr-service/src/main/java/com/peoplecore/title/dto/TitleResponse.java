package com.peoplecore.title.dto;

import com.peoplecore.title.domain.Title;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TitleResponse {
    private Long titleId;
    private String titleName;
    private String titleCode;
    private Long deptId;
    private String deptName;

    public static TitleResponse from(Title title, String deptName) {
        return TitleResponse.builder()
                .titleId(title.getTitleId())
                .titleName(title.getTitleName())
                .titleCode(title.getTitleCode())
                .deptId(title.getDeptId())
                .deptName(deptName)
                .build();
    }
}
