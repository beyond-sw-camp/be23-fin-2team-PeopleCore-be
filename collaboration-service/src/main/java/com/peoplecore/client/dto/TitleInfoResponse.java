package com.peoplecore.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TitleInfoResponse {
    private Long titleId;
    private UUID companyId;
    private Long deptId;
    private String titleName;
    private String titleCode;
}
