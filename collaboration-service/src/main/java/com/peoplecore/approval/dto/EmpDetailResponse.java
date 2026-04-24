package com.peoplecore.approval.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmpDetailResponse {
    private String empName;
    private Long deptId;
    private String gradeName;
    private String titleName;
    private String deptName;


}
