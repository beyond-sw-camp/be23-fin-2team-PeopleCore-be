package com.peoplecore.approval.controller;

import com.peoplecore.approval.dto.ApprovalCommentRequest;
import com.peoplecore.approval.dto.ApprovalCommentResponse;
import com.peoplecore.approval.service.ApprovalCommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/approval/document/{docId}/comments")
@RequiredArgsConstructor
public class ApprovalCommentController {

    private final ApprovalCommentService commentService;

    /** 댓글 목록 조회 */
    @GetMapping
    public ResponseEntity<List<ApprovalCommentResponse>> getComments(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long docId) {
        return ResponseEntity.ok(commentService.getComments(companyId, docId));
    }

    /** 댓글 작성 */
    @PostMapping
    public ResponseEntity<ApprovalCommentResponse> create(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestHeader("X-User-Name") String empName,
            @RequestHeader("X-User-Department") Long deptId,
            @RequestHeader("X-User-Grade") String empGrade,
            @RequestHeader(value = "X-User-Title", required = false) String empTitle,
            @PathVariable Long docId,
            @RequestBody ApprovalCommentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commentService.create(companyId, empId, empName, deptId, empGrade, empTitle, docId, request));
    }

    /** 댓글 수정 */
    @PutMapping("/{commentId}")
    public ResponseEntity<ApprovalCommentResponse> update(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long docId,
            @PathVariable Long commentId,
            @RequestBody ApprovalCommentRequest request) {
        return ResponseEntity.ok(commentService.update(companyId, empId, commentId, request));
    }

    /** 댓글 삭제 */
    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @PathVariable Long docId,
            @PathVariable Long commentId) {
        commentService.delete(companyId, empId, commentId);
        return ResponseEntity.noContent().build();
    }
}
