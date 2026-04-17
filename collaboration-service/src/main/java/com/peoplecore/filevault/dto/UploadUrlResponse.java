package com.peoplecore.filevault.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UploadUrlResponse {
    private String uploadUrl;
    private String storageKey;
}
