package com.peoplecore.attendance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/*회사의 인사팀 사원 목록 Response.
 * GET /attendance/modify/hr-members
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceModifyHrMemberResDto {

    /* HR 사원 목록
     */
    private List<HrMember> hrMembers;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class HrMember {
        /*사원 ID
         */
        private Long empId;

        /*
         * 사원 이름
         */
        private String empName;

        /*
         * 부서명 (nullable)
         */
        private String deptName;

        /*
         * 직급명 (nullable)
         */
        private String gradeName;

        /*
         * 직책명 (nullable)
         */
        private String titleName;

        /*
         * 역할 — HR_ADMIN / HR_SUPER_ADMIN
         */
        private String empRole;
    }
}