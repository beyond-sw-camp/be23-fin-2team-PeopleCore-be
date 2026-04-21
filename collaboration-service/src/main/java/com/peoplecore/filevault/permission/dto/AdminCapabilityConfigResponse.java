package com.peoplecore.filevault.permission.dto;

import com.peoplecore.filevault.permission.entity.FileBoxAdminMode;
import lombok.*;

import java.util.List;

/**
 * HR 통합 화면 — 현재 회사의 파일함 Admin 권한 모드 + 대상 ID 목록 응답.
 *
 * <p>모드 (GRADE/TITLE) 와 그 모드에서 부여된 target id 들만 반환한다.
 * 직급/직책 메타(이름·인원수)는 FE 가 별도 org API 로 합성한다.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminCapabilityConfigResponse {
    private FileBoxAdminMode mode;
    private List<Long> grantedTargetIds;
}
