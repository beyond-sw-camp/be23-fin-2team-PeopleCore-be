package com.peoplecore.filevault.dto;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FavoriteListResponse {
    private List<FolderResponse> folders;
    private List<FileResponse> files;
}
