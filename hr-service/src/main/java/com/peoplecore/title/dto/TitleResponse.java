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
    private Integer titleOrder;

    public static TitleResponse from(Title title) {
        return TitleResponse.builder()
                .titleId(title.getTitleId())
                .titleName(title.getTitleName())
                .titleCode(title.getTitleCode())
                .titleOrder(title.getTitleOrder())
                .build();
    }
}
