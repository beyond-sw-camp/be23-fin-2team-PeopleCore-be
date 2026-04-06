package com.peoplecore.pay.controller;

import com.peoplecore.auth.RoleRequired;
import com.peoplecore.pay.domain.QTaxWithholdingTable;
import com.peoplecore.pay.domain.TaxWithholdingTable;
import com.peoplecore.pay.dtos.TaxWithholdingResDto;
import com.peoplecore.pay.service.TaxWithholdingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/pay/superadmin/taxtable")
@RoleRequired("{HR_SUPER_ADMIN}")
public class TaxWithholdingController {

    private final TaxWithholdingService taxWithholdingService;
    @Autowired
    public TaxWithholdingController(TaxWithholdingService taxWithholdingService) {
        this.taxWithholdingService = taxWithholdingService;
    }

    //    등록된 연도 목록 조회
    @GetMapping("/years")
    public ResponseEntity<List<Integer>> getYearList(){
        return ResponseEntity.ok(taxWithholdingService.getYearList());
    }

//    특정연도 세액표 조회(페이징)
    @GetMapping("/{year}")
    public ResponseEntity<Page<TaxWithholdingResDto>> getTableByYear(@PathVariable Integer year, @PageableDefault(size = 50, sort = "salaryFrom", direction = Sort.Direction.ASC) Pageable pageable){

        return ResponseEntity.ok(taxWithholdingService.getTableByYear(year, pageable));
    }

}
