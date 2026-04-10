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
public class ResignDetailDto {

    private Long resignId;
    private Long docId; //양식 렌더링용
    private String empNum;
    private String empName;
    private String deptName;
    private String gradeName;
    private LocalDate hireDate;
    private String empStatus;
    private String approvalStatus;  //재직상태
    private LocalDate registeredDate;   //결재상태

    public static ResignDetailDto fromEntity(Resign resign){
        return ResignDetailDto.builder()
                .resignId(resign.getResignId())
                .docId(resign.getDocId())
                .empNum(resign.getEmployee().getEmpNum())
                .empName(resign.getEmployee().getEmpName())
                .deptName(resign.getDepartment().getDeptName())
                .gradeName(resign.getGrade().getGradeName())
                .empStatus(resign.getRetireStatus().name())
                .approvalStatus(resign.getApprovalStatus().name())
                .registeredDate(resign.getRegisteredDate())
                .build();
    }

}
