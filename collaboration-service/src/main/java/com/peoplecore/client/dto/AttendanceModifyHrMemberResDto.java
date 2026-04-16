package com.peoplecore.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/* hr-service 의 GET /attendance/modify/hr-members 응답 매핑 DTO */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceModifyHrMemberResDto {

    private List<HrMember> hrMembers;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class HrMember {
        private Long empId;
        private String empName;
        private String deptName;
        private String gradeName;
        private String titleName;
        private String empRole;
    }
}