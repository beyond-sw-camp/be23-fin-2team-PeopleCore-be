package com.peoplecore.pay.controller;

import com.peoplecore.auth.RoleRequired;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.pay.approval.*;
import com.peoplecore.pay.dtos.*;
import com.peoplecore.pay.service.PayrollService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/pay/admin/payroll")
@RoleRequired({"HR_SUPER_ADMIN","HR_ADMIN"})
public class PayrollController {

    private final PayrollService payrollService;
    private final PayrollApprovalDraftService payrollApprovalDraftService;
    private final PayrollApprovalSnapshotRepository snapshotRepository;

    @Autowired
    public PayrollController(PayrollService payrollService, PayrollApprovalDraftService payrollApprovalDraftService, PayrollApprovalSnapshotRepository snapshotRepository) {
        this.payrollService = payrollService;
        this.payrollApprovalDraftService = payrollApprovalDraftService;
        this.snapshotRepository = snapshotRepository;
    }

//    급여대장 조회
    @GetMapping
    public ResponseEntity<PayrollRunResDto> getPayroll(
            @RequestHeader("X-User-Company")UUID companyId,
            @RequestParam String payYearMonth){
        return ResponseEntity.ok(payrollService.getPayroll(companyId, payYearMonth));
    }

//    급여산정 생성
    @PostMapping("/create")
    public ResponseEntity<PayrollRunResDto> createPayroll(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestParam String payYearMonth){
        return ResponseEntity.status(HttpStatus.CREATED).body(payrollService.createPayroll(companyId, payYearMonth));
    }


//    사원별 급여 상세
    @GetMapping("/{payrollRunId}/employees/{empId}")
    public ResponseEntity<PayrollEmpDetailResDto> getEmpPayrollDetail(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long payrollRunId,
            @PathVariable Long empId ) {
        return ResponseEntity.ok(payrollService.getEmpPayrollDetail(companyId, payrollRunId, empId));
    }

//    사원별 상세-항목별 금액 수정
    @PutMapping("/{payrollRunId}/employees/{empId}/details")
    public ResponseEntity<Void> updateEmpDetails(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long payrollRunId,
            @PathVariable Long empId,
            @RequestBody PayrollDetailUpdateReqDto reqDto){
        payrollService.updateEmpDetails(companyId, payrollRunId, empId, reqDto);
        return ResponseEntity.ok().build();
    }

//    급여 확정(개인별)
    @PutMapping("/{payrollRunId}/employees/{empId}/confirm")
    public ResponseEntity<Void> confirmEmployee(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long actorEmpId,
            @PathVariable Long payrollRunId,
            @PathVariable Long empId) {
        payrollService.confirmEmployee(companyId, payrollRunId, empId, actorEmpId);
        return ResponseEntity.ok().build();
    }

//    확정급여 되돌리기(개인별)
    @PutMapping("/{payrollRunId}/employees/{empId}/revert")
    public ResponseEntity<Void> revertEmployee(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long payrollRunId,
            @PathVariable Long empId) {
        payrollService.revertEmployee(companyId, payrollRunId, empId);
        return ResponseEntity.ok().build();
    }

///    전자결재
//  1. 전자결재 미리보기 데이터 조회 (모달 열때 호출)
    @GetMapping("/{payrollRunId}/approval/draft")
    public ResponseEntity<ApprovalDraftResDto> draftDtos(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long payrollRunId){
        return ResponseEntity.ok(payrollApprovalDraftService.draft(companyId, userId, payrollRunId));
    }

//    지급처리
    @PutMapping("/{payrollRunId}/pay")
    public ResponseEntity<Void> processPayment(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long payrollRunId){
        payrollService.processPayment(companyId, payrollRunId);
        return ResponseEntity.ok().build();
    }

//    선택 사원 대량이체 파일 다운로드
    @PostMapping("/{payrollRunId}/transfer-file")
    public ResponseEntity<byte[]> downloadTransferFile(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long payrollRunId,
            @RequestBody List<Long> empIds){

        TransferFileResDto result = payrollService.generateTransferFile(companyId, payrollRunId, empIds);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=" + URLEncoder.encode(result.getFileName(), StandardCharsets.UTF_8))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(result.getFileBytes());
    }

//    일당/시급 기준 조회
    @GetMapping("/{payrollRunId}/employees/{empId}/wage-info")
    public ResponseEntity<WageInfoResDto> getWageInfo(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long payrollRunId,
            @PathVariable Long empId){
        return ResponseEntity.ok(payrollService.getWageInfo(companyId, payrollRunId, empId));
    }

//    이달 승인된 전자결재 조회
    @GetMapping("/{payrollRunId}/employees/{empId}/approved-overtime")
    public ResponseEntity<ApprovedOvertimeResDto> getApprovedOvertime(
            @RequestHeader("X-User-Company") UUID companyID,
            @PathVariable Long payrollRunId,
            @PathVariable Long empId){
        return ResponseEntity.ok(
                payrollService.getApprovedOvertime(companyID,payrollRunId, empId));
    }

//    초과근무 수당 적용
    @PostMapping("/{payrollRunId}/employees/{empId}/apply-overtime")
    public ResponseEntity<Void> applyOvertime(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long payrollRunId,
            @PathVariable Long empId){
        payrollService.applyOverTime(companyId,payrollRunId,empId);
        return ResponseEntity.ok().build();
        }


//    지급합계 기반 공제항목 실시간 계산
    @PostMapping("/calc-deductions")
    public ResponseEntity<CalcDeductionResDto> calcDeductions(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestBody CalcDeductionReqDto reqDto) {
        return ResponseEntity.ok(
                payrollService.calcDeductions(companyId, reqDto));
    }


//    전자결재 스냅샷
    @GetMapping("/admin/approval/{docId}/snapshot")
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
