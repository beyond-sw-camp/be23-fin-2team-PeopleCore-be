package com.peoplecore.menusetting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * 메뉴 1건 업데이트 요청
 * - menuCode: SidebarMenu enum jobTypeName (잘못된 값이면 service 에서 IllegalArgumentException)
 * - isVisible: TOGGLEABLE 메뉴만 반영됨, 나머지는 true 로 강제
 * - sortOrder: 모든 메뉴에서 반영
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserMenuItemRequest {

    @NotBlank
    private String menuCode;

    @NotNull
    private Boolean isVisible;

    @NotNull
    private Integer sortOrder;
}
