package com.peoplecore.salarycontract.controller;

import com.peoplecore.salarycontract.domain.SalaryContractSortField;
import com.peoplecore.salarycontract.dto.SalaryContractCreateReqDto;
import com.peoplecore.salarycontract.dto.SalaryContractDetailResDto;
import com.peoplecore.salarycontract.dto.SalaryContractHisToryResDto;
import com.peoplecore.salarycontract.dto.SalaryContractListResDto;
import com.peoplecore.salarycontract.service.SalaryContractService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;


import java.util.List;
import java.util.UUID;

import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;


@RestController
@RequestMapping("/salary-contract")
public class SalaryContractController {

    private final SalaryContractService salaryContractService;

    @Autowired
    public SalaryContractController(SalaryContractService salaryContractService) {
        this.salaryContractService = salaryContractService;
    }

    //    1.목록조회
    @GetMapping
    public ResponseEntity<Page<SalaryContractListResDto>> list(@RequestHeader("X-User-Company") String companyId,
                                                               @RequestParam(required = false) String search,
                                                               @RequestParam(required = false) String year,
                                                               @RequestParam(required = false) SalaryContractSortField sortField,
                                                               Pageable pageable) {
        return ResponseEntity.ok(salaryContractService.list(UUID.fromString(companyId), search, year, sortField, pageable));
    }

    //    2. 계약서 등록
    @PostMapping(consumes = MULTIPART_FORM_DATA_VALUE) // 파일첨부
    public ResponseEntity<SalaryContractDetailResDto> create(@RequestHeader("X-User-Company") String companyId,
                                                             @RequestHeader("X-User-Id")Long userId,
                                                             @RequestPart("data") @Valid SalaryContractCreateReqDto req,
                                                             @RequestPart(value = "attachment", required = false) MultipartFile file) {
        return ResponseEntity.ok(salaryContractService.create(UUID.fromString(companyId), userId, req, file));
    }

    //  3. 상세조회
    @GetMapping("/{id}")
    public ResponseEntity<SalaryContractDetailResDto> detail(@RequestHeader("X-User-Company") String companyId,
                                                          @PathVariable Long id) {
        return ResponseEntity.ok(salaryContractService.detail(UUID.fromString(companyId), id));
    }
//    4.사원별 계약 이력
    @GetMapping("/history/{empId}")
    public ResponseEntity<List<SalaryContractHisToryResDto>>historysnap(@RequestHeader("X-User-Company") String companyId,
                                                                    @PathVariable Long empId){
        return ResponseEntity.ok(salaryContractService.historysnap(UUID.fromString(companyId),empId));
    }
//
//    5.삭제(softDelete
    @DeleteMapping("/{id}")
    public ResponseEntity<Void>delete(@RequestHeader("X-User-Company") String companyId,
                                      @PathVariable Long id) {
        return ResponseEntity.noContent().build();
    }
}

