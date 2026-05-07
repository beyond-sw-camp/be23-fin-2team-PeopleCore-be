package com.peoplecore.department.dto;

import com.peoplecore.employee.domain.Employee;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class OrgChartMemberDto {
    private Long empId;
    private String empName;
    private String gradeName;
    private String titleName;
    private String profileImageUrl;

    public static OrgChartMemberDto from(Employee e) {
        return OrgChartMemberDto.builder()
                .empId(e.getEmpId())
                .empName(e.getEmpName())
                .gradeName(e.getGrade().getGradeName())
                .titleName(e.getTitle() != null ? e.getTitle().getTitleName() : null)
                .profileImageUrl(e.getEmpProfileImageUrl())
                .build();
    }
}
