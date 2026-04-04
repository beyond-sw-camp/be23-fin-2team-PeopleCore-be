package com.peoplecore.company.controller;

import com.peoplecore.company.dtos.CompanyCreateReqDto;
import com.peoplecore.company.dtos.CompanyResDto;
import com.peoplecore.company.service.CompanyService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/companies")
public class InternalCompanyController {

    private final CompanyService companyService;
    @Autowired
    public InternalCompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

//    회사등록
    @PostMapping
    public ResponseEntity<CompanyResDto> createCompany(@RequestBody @Valid CompanyCreateReqDto reqDto){
        return ResponseEntity.status(HttpStatus.CREATED).body(companyService.createCompany(reqDto));
    }
}
