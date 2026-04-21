package com.peoplecore.filevault.permission.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileBoxAclUpdateRequest {

    @NotNull
    private Boolean canRead;

    @NotNull
    private Boolean canWrite;

    @NotNull
    private Boolean canDownload;

    @NotNull
    private Boolean canDelete;
}
