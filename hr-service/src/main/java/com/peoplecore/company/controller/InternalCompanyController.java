package com.peoplecore.company.controller;


import com.peoplecore.company.dtos.InternalCompanyResponseDto;
import com.peoplecore.company.entity.Company;
import com.peoplecore.company.repository.CompanyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/company")
public class InternalCompanyController {
    private final CompanyRepository companyRepository;

    @Autowired
    public InternalCompanyController(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    @GetMapping("/{companyId}")
    public ResponseEntity<InternalCompanyResponseDto> getCompany(@PathVariable UUID companyId) {
        Company company = companyRepository.findById(companyId).orElseThrow(() -> new RuntimeException("회사를 찾을 수 없습니다."));

        return ResponseEntity.ok(InternalCompanyResponseDto.from(company));
    }
}
