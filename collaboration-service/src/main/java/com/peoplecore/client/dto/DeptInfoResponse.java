package com.peoplecore.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeptInfoResponse {
    private Long deptId;
    private String deptName;
    private String deptCode;
}