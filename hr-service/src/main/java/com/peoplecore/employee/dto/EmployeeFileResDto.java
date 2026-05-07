package com.peoplecore.employee.dto;

import com.peoplecore.employee.domain.EmployeeFile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeFileResDto {
    private Long id;
    private String originalFileName;
    private String contentType;
    private Long fileSize;

    public static EmployeeFileResDto from(EmployeeFile f) {
        return EmployeeFileResDto.builder()
                .id(f.getId())
                .originalFileName(f.getOriginalFileName())
                .contentType(f.getContentType())
                .fileSize(f.getFileSize())
                .build();
    }
}
