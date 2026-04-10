package com.peoplecore.approval.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalCommentRequest {
    private Long parentCommentId;
    private String content;
}
