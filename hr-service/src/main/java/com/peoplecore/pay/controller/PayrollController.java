package com.peoplecore.pay.controller;

import com.peoplecore.auth.RoleRequired;
import com.peoplecore.pay.dtos.PayrollEmpDetailResDto;
import com.peoplecore.pay.dtos.PayrollRunResDto;
import com.peoplecore.pay.dtos.TransferFileResDto;
import com.peoplecore.pay.service.PayrollService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/pay/admin/payroll")
@RoleRequired({"HR_SUPER_ADMIN","HR_ADMIN"})
public class PayrollController {

    private final PayrollService payrollService;

    @Autowired
    public PayrollController(PayrollService payrollService) {
        this.payrollService = payrollService;
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

//    전월복사
    @PostMapping("/copy")
    public ResponseEntity<PayrollRunResDto> copyFromPreviousMonth(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestParam String payYearMonth){
        return ResponseEntity.status(HttpStatus.CREATED).body(payrollService.copyFromPreviousMonth(companyId, payYearMonth));
    }

//    사원별 급여 상세
    @GetMapping("/{payrollRunId}/employees/{empId}")
    public ResponseEntity<PayrollEmpDetailResDto> getEmpPayrollDetail(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long payrollRunId,
            @PathVariable Long empId ) {
        return ResponseEntity.ok(payrollService.getEmpPayrollDetail(companyId, payrollRunId, empId));
    }

//    급여 확정
    @PutMapping("/{payrollRunId}/confirm")
    public ResponseEntity<Void> confirmPayroll(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long payrollRunId){
        payrollService.confirmPayroll(companyId, payrollRunId);
        return ResponseEntity.ok().build();
    }

//    전자결재 상신
    @PostMapping("/{payrollRunId}/submit-approval")
    public ResponseEntity<Void> submitApproval(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long payrollRunId,
            @RequestParam Long approvalDocId){  //?? 프론트에서 받아오기
        payrollService.submitApproval(companyId, payrollRunId, approvalDocId);
        return ResponseEntity.ok().build();
    }

//    지급처리
    @PutMapping("/{payrollRunId}/pay")
    public ResponseEntity<Void> processPayment(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long payrollRunId){
        payrollService.processPayment(companyId, payrollRunId);
        return ResponseEntity.ok().build();
    }

//    대량이체 파일 다운로드
    @GetMapping("/{payrollRunId}/transfer-file")
    public ResponseEntity<byte[]> downloadTransferFile(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long payrollRunId){

        TransferFileResDto result =payrollService.generateTransferFile(companyId, payrollRunId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=" + URLEncoder.encode(result.getFileName(), StandardCharsets.UTF_8))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(result.getFileBytes());

    }
}
