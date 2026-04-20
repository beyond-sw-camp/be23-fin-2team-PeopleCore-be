package com.peoplecore.filevault.dto;

import com.peoplecore.filevault.entity.FavoriteTargetType;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FavoriteToggleResponse {
    private FavoriteTargetType targetType;
    private Long targetId;
    private boolean starred;
}
