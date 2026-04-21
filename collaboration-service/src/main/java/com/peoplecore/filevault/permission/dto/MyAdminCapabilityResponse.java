package com.peoplecore.filevault.permission.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * 내가 파일함 Admin 권한이 있는지 (= COMPANY/DEPT 파일함 생성 가능 여부).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MyAdminCapabilityResponse {
    @JsonProperty("isAdmin")
    private boolean isAdmin;
}
