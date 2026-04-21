package com.peoplecore.filevault.permission.dto;

import com.peoplecore.filevault.permission.entity.FileBoxAdminMode;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

/**
 * HR 통합 화면 — 모드 + 대상 id 목록을 PUT 으로 원자적 교체.
 *
 * <p>{@code grantedTargetIds} 가 비면 0-admin 상태가 되므로 서비스 단에서 거부한다.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminCapabilityUpdateRequest {

    @NotNull
    private FileBoxAdminMode mode;

    @NotEmpty
    private List<Long> grantedTargetIds;
}
