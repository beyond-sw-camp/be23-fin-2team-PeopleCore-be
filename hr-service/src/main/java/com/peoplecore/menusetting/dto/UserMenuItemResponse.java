package com.peoplecore.menusetting.dto;

import com.peoplecore.menusetting.domain.SidebarMenu;
import com.peoplecore.menusetting.domain.UserMenuSetting;
import lombok.*;

/**
 * 사이드바 메뉴 1건 응답 DTO
 * - menuCode: SidebarMenu enum jobTypeName (프론트 라우팅 매칭용)
 * - isVisible: 실제 프론트에서 렌더할지 여부 (alwaysOn 이면 무조건 true)
 * - sortOrder: 사이드바 표시 순서
 * - toggleable: true 면 설정 모달에서 on/off 토글 UI 노출 (DASHBOARD 외 전부 true)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserMenuItemResponse {
    private String menuCode;
    private Boolean isVisible;
    private Integer sortOrder;
    private Boolean toggleable;

    public static UserMenuItemResponse of(UserMenuSetting entity, boolean effectiveVisible) {
        SidebarMenu menu = entity.getMenuCode();
        return UserMenuItemResponse.builder()
                .menuCode(menu.name())
                .isVisible(effectiveVisible)
                .sortOrder(entity.getSortOrder())
                .toggleable(!menu.isAlwaysOn())
                .build();
    }
}
