package com.peoplecore.approval.dto;

import com.peoplecore.common.entity.CommonComment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalCommentResponse {
    private Long commentId;
    private Long parentCommentId;
    private Long empId;
    private String empName;
    private String empDeptName;
    private String empGradeName;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ApprovalCommentResponse from(CommonComment comment) {
        return ApprovalCommentResponse.builder()
                .commentId(comment.getCommentId())
                .parentCommentId(comment.getParentCommentId())
                .empId(comment.getEmpId())
                .empName(comment.getEmpName())
                .empDeptName(comment.getEmpDeptName())
                .empGradeName(comment.getEmpGradeName())
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }
}
