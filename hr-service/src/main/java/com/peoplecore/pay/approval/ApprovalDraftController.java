package com.peoplecore.pay.approval;

import com.peoplecore.auth.RoleRequired;
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

    @Autowired
    public ApprovalDraftController(ApprovalDraftFacade facade) {
        this.facade = facade;
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


//    전자결재 상신
    @PostMapping("/submit")
    public ResponseEntity<Void> submit(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody @Valid ApprovalSubmitReqDto reqDto){
        facade.submit(companyId, userId, reqDto);
        return ResponseEntity.ok().build();
    }
}
