package com.peoplecore.approval.dto;

import com.peoplecore.common.entity.CommonAttachFile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalSignatureResponseDto {
    private Long attachId;
    private Long sigEmpId;
    private String originalFileName;
    private String fileUrl;
    private Long fileSize;
    private Long sigManagerId;

    /*조회 데이터는 commonAttach에서 하고 인사취고 권위자가 등록해주는건 approvalsignature에서 조회 */
    public static ApprovalSignatureResponseDto from(CommonAttachFile attachFile, Long sigManagerId, String presignedUrl) {
        return ApprovalSignatureResponseDto.builder()
                .attachId(attachFile.getAttachId())
                .sigEmpId(attachFile.getEntityId())
                .originalFileName(attachFile.getOriginalFileName())
                .fileUrl(presignedUrl)
                .fileSize(attachFile.getFileSize())
                .sigManagerId(sigManagerId)
                .build();
    }
}
