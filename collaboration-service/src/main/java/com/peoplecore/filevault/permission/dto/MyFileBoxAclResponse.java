package com.peoplecore.filevault.permission.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * 내 ACL — folder 단위 4-플래그.
 *
 * <p>{@code isOwner} true 면 항상 4-플래그 모두 true (감사 일관성).
 * ACL 행 자체가 없는 경우 호출 측에서 default-locked (모두 false) 로 해석한다.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MyFileBoxAclResponse {
    private Long folderId;
    @JsonProperty("isOwner")
    private boolean isOwner;
    private boolean canRead;
    private boolean canWrite;
    private boolean canDownload;
    private boolean canDelete;
}
