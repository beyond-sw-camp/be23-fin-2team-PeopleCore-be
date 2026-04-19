package com.peoplecore.filevault.dto;

import com.peoplecore.filevault.entity.ActivityAction;
import com.peoplecore.filevault.entity.FileVaultActivity;
import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityResponse {
    private Long id;
    private ActivityAction action;
    private String targetName;
    private String location;
    private String userName;
    private LocalDateTime createdAt;

    public static ActivityResponse from(FileVaultActivity activity) {
        return ActivityResponse.builder()
            .id(activity.getId())
            .action(activity.getAction())
            .targetName(activity.getTargetName())
            .location(activity.getLocation())
            .userName(activity.getUserName())
            .createdAt(activity.getCreatedAt())
            .build();
    }
}
