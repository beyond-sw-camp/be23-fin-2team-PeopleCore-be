package com.peoplecore.pay.approval;

import com.peoplecore.auth.RoleRequired;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/pay/admin/approval")
@RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
public class ApprovalDraftController {

    private final ApprovalDraftFacade facade;
    private final PayrollApprovalSnapshotRepository snapshotRepository;

    @Autowired
    public ApprovalDraftController(ApprovalDraftFacade facade, PayrollApprovalSnapshotRepository snapshotRepository) {
        this.facade = facade;
        this.snapshotRepository = snapshotRepository;
    }

//    전자결재 데이터 조회(미리보기)
    @GetMapping("/draft")
    public ResponseEntity<ApprovalDraftResDto> draft(
            @RequestHeader("X-User-Company")UUID companyId,
            @RequestHeader("X-User-Id") Long userID,
            @RequestParam ApprovalFormType type,
            @RequestParam Long ledgerId){
        return ResponseEntity.ok(facade.draft(companyId, userID, type, ledgerId));
    }


    //    전자결재 스냅샷
    @GetMapping("/{docId}/snapshot")
    public ResponseEntity<ApprovalSnapshotResDto> getSnapshot(@PathVariable Long docId) {
        PayrollApprovalSnapshot snapshot = snapshotRepository.findByApprovalDocId(docId)
                .orElseThrow(() -> new CustomException(ErrorCode.APPROVAL_SNAPSHOT_NOT_FOUND));

        return ResponseEntity.ok(ApprovalSnapshotResDto.builder()
                .approvalDocId(docId)
                .approvalType(snapshot.getApprovalType())
                .htmlSnapshot(snapshot.getHtmlSnapshot())
                .createdAt(snapshot.getCreatedAt())
                .build());
    }

}
