package com.peoplecore.approval.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonalFolderTransferRequest {
    /*이관 대상 사원 id */
    private Long targetEmpId;

}
