package com.peoplecore.resign.dto;

import com.peoplecore.resign.domain.Resign;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;


@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class ResignListDto {

    private Long id;
    private String empNum;
    private String empName;
    private String deptName;
    private String gradeName;
    private String empStatus; //재직상태 active, resigned
    private String approvalStatus; // 결재상태 pending, approved
    private LocalDate resignDate; //퇴직예정일자
    private LocalDate registeredDate; //신청일

    public static ResignListDto fromEntity(Resign resign){
        return ResignListDto.builder()
                .id(resign.getResignId())
                .empNum(resign.getEmployee().getEmpNum())
                .empName(resign.getEmployee().getEmpName())
                .deptName(resign.getDepartment().getDeptName())
                .gradeName(resign.getGrade().getGradeName())
                .empStatus(resign.getRetireStatus().name())
                .approvalStatus(resign.getApprovalStatus().name())
                .resignDate(resign.getResignDate())
                .registeredDate(resign.getRegisteredDate())
                .build();
    }
}
