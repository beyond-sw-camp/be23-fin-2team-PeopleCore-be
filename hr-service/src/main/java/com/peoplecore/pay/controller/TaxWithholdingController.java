package com.peoplecore.pay.controller;

import com.peoplecore.auth.RoleRequired;
import com.peoplecore.pay.domain.QTaxWithholdingTable;
import com.peoplecore.pay.domain.TaxWithholdingTable;
import com.peoplecore.pay.dtos.TaxWithholdingResDto;
import com.peoplecore.pay.dtos.TaxWithholdingRowResDto;
import com.peoplecore.pay.service.TaxWithholdingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/pay/superadmin/taxtable")
@RoleRequired({"HR_SUPER_ADMIN"})
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
    public ResponseEntity<Page<TaxWithholdingRowResDto>> getTableByYear(@PathVariable Integer year, @PageableDefault(size = 50, sort = "salaryMin", direction = Sort.Direction.ASC) Pageable pageable){

        return ResponseEntity.ok(taxWithholdingService.getTableByYear(year, pageable));
    }

    // 간이세액표 엑셀 업로드 (운영자용 — 매년 1회)
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("year") Integer year, @RequestParam("file") MultipartFile file) throws IOException {

        int inserted = taxWithholdingService.uploadFromExcel(year, file);
        return ResponseEntity.ok(Map.of(
                "year", year,
                "inserted", inserted,
                "message", year + "년 간이세액표 " + inserted + "행 등록 완료"
        ));
    }
}
