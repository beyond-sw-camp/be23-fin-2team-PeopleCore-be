package com.peoplecore.department.dto;

import com.peoplecore.department.domain.Department;
import com.peoplecore.employee.domain.Employee;
import lombok.*;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DepartmentDetailResponse {

    private Long deptId;
    private String deptName;
    private String deptCode;
    private List<TitleHolderInfo> titleHolders;
    private long activeCount;
    private int childDeptCount;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class TitleHolderInfo {
        private Long empId;
        private String empName;
        private String titleName;
        private String gradeName;
        private String empProfileImageUrl;

        public static TitleHolderInfo from(Employee employee) {
            return TitleHolderInfo.builder()
                    .empId(employee.getEmpId())
                    .empName(employee.getEmpName())
                    .titleName(employee.getTitle().getTitleName())
                    .gradeName(employee.getGrade().getGradeName())
                    .empProfileImageUrl(employee.getEmpProfileImageUrl())
                    .build();
        }
    }

    public static DepartmentDetailResponse of(
            Department dept,
            List<Employee> titleHolders,
            long activeCount,
            int childDeptCount
    ) {
        return DepartmentDetailResponse.builder()
                .deptId(dept.getDeptId())
                .deptName(dept.getDeptName())
                .deptCode(dept.getDeptCode())
                .titleHolders(titleHolders.stream()
                        .map(TitleHolderInfo::from)
                        .toList())
                .activeCount(activeCount)
                .childDeptCount(childDeptCount)
                .build();
    }
}