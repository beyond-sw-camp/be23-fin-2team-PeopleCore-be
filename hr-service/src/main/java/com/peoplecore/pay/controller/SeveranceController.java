package com.peoplecore.pay.controller;

import com.peoplecore.auth.RoleRequired;
import com.peoplecore.pay.dtos.*;
import com.peoplecore.pay.enums.SevStatus;
import com.peoplecore.pay.service.SeveranceService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.awt.print.Pageable;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/pay/admin/severance")
@RoleRequired({"HR_SUPER_ADMIN","HR_ADMIN"})
public class SeveranceController {

    private final SeveranceService severanceService;

    @Autowired
    public SeveranceController(SeveranceService severanceService) {
        this.severanceService = severanceService;
    }

//    퇴직금 산정
    @PostMapping("/calculate")
    public ResponseEntity<SeveranceDetailResDto> calculate(
            @RequestHeader("X-User-Company")UUID companyId,
            @RequestBody @Valid SeveranceCalcReqDto reqDto){
        return ResponseEntity.status(HttpStatus.CREATED).body(severanceService.calculateSeverance(companyId, reqDto));
    }

////    퇴직금 목록 조회
//    @GetMapping
//    public ResponseEntity<SeveranceListResDto> list(
//            @RequestHeader("X-User-Company")UUID companyId,
//            @RequestParam(required = false)SevStatus status,
//            @PageableDefault(size = 10)Pageable pageable){
//        return ResponseEntity.ok(severanceService.getSeveranceList(companyId, status, pageable));
//    }
//
////    퇴직금 상세 조회
//    @GetMapping("/{sevId}")
//    public ResponseEntity<SeveranceDetailResDto> detail(
//            @RequestHeader("X-User-Company") UUID companyId,
//            @PathVariable Long sevId) {
//        return ResponseEntity.ok().body(severanceService.getSeveranceDetail(companyId, sevId));
//    }
//
////    퇴직금 확정
//    @PutMapping("/{sevId}/confirm")
//    public ResponseEntity<Void> confirm(
//            @RequestHeader("X-User-Company") UUID companyId,
//            @RequestHeader("X-User-EmpId") Long empId,
//            @PathVariable Long sevId){
//        severanceService.confirmSeverance(companyId, sevId, empId);
//        return ResponseEntity.ok().build();
//    }
//
////    전자결재 상신
//    @PutMapping("/{sevId}/submit-approval")
//    public ResponseEntity<Void> submitApproval(
//            @RequestHeader("X-User-Company") UUID companyId,
//            @PathVariable Long sevId,
//            @RequestParam Long approvalDocId){
//        severanceService.submitApproval(companyId, sevId, approvalDocId);
//    }
//
////    지급 처리
//    @PutMapping("/{sevId}/pay")
//    public ResponseEntity<Void> pay(
//            @RequestHeader("X-User-Company") UUID companyId,
//            @RequestHeader("X-User-EmpId") Long empId,
//            @PathVariable Long sevId,
//            @RequestBody @Valid SeverancePayReqDto reqDto){
//        severanceService.processPayment(companyId, sevId, empId, reqDto.getTransferDate());
//        return ResponseEntity.ok().build();
//    }
//
////    이체파일 생성 (선택 건)
//    @PostMapping("/transfer-file")
//    public ResponseEntity<byte[]> transferFile(
//            @RequestHeader("X-User-Company") UUID companyId,
//            @RequestBody List<Long> sevIds) {
//        TransferFileResDto file = severanceService.generateTransferFile(companyId, sevIds);
//        return ResponseEntity.ok()
//                .header("Content-Disposition",
//                        "attachment; filename=\"" + file.getFileName() + "\"")
//                .header("Content-Type", "text/csv; charset=UTF-8")
//                .body(file.getFileBytes());
//    }




}

