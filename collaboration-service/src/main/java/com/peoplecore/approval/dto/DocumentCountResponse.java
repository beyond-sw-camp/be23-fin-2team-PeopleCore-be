package com.peoplecore.approval.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentCountResponse {
    /* 결재하기 */
    private long waiting;
    private long ccView;
    private long upcoming;

    /* 개인 문서함 */
    private long draft;
    private long temp;
    private long approved;
    private long ccViewBox;
    private long inbox;

    /* 부서 문서함 */
    private long deptCompleted;
    private long deptReceived;
    private long deptSent;

    /* 부서 폴더별 문서 개수 (deptFolderId → count) */
    private Map<Long, Long> deptFolderCounts;

    /* 개인 폴더별 문서 개수 (personalFolderId → count) */
    private Map<Long, Long> personalFolderCounts;
}
