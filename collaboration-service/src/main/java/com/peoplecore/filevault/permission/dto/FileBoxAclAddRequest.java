package com.peoplecore.filevault.permission.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileBoxAclAddRequest {

    @NotNull
    private Long empId;

    /** 기본값 적용 시 read/download true, write/delete false. */
    private Boolean canRead;
    private Boolean canWrite;
    private Boolean canDownload;
    private Boolean canDelete;
}
