package com.peoplecore.menusetting.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.List;

/**
 * 사이드바 설정 일괄 저장 요청
 * - items: 프론트에서 드래그/토글 후 전체 배열을 그대로 전송
 *   (정렬 순서대로 배열이 와야 함 - 배열 인덱스 기반이 아니라 item.sortOrder 를 그대로 사용)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserMenuSettingUpdateRequest {

    @NotEmpty(message = "메뉴 항목이 비어 있습니다.")
    @Valid
    private List<UserMenuItemRequest> items;
}
