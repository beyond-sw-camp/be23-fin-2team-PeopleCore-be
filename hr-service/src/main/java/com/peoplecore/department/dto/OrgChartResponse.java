package com.peoplecore.department.dto;

import com.peoplecore.department.domain.Department;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
@Builder
public class OrgChartResponse {
    private Long id;
    private String deptName;
    private String deptCode;
    private List<OrgChartMemberDto> members;
    private List<OrgChartResponse> children;

    public static OrgChartResponse of(Department dept, List<OrgChartMemberDto> members, List<OrgChartResponse> children) {
        return OrgChartResponse.builder()
                .id(dept.getDeptId())
                .deptName(dept.getDeptName())
                .deptCode(dept.getDeptCode())
                .members(members)
                .children(children)
                .build();
    }
}
