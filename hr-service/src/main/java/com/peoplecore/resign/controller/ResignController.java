package com.peoplecore.resign.controller;

import com.peoplecore.resign.domain.ResignSortField;
import com.peoplecore.resign.dto.ResignDetailDto;
import com.peoplecore.resign.dto.ResignListDto;
import com.peoplecore.resign.dto.ResignStatusDto;
import com.peoplecore.resign.service.ResignService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/resign")
public class ResignController {

    private final ResignService resignService;

    @Autowired
    public ResignController(ResignService resignService) {
        this.resignService = resignService;
    }

//    1.목록조회
    @GetMapping
    public ResponseEntity<Page<ResignListDto>>getResignList(@RequestHeader("X-User-Company") UUID companyId,
                                                            @RequestParam(required = false)String keyword,
                                                            @RequestParam(required = false)String empStatus,
                                                            @RequestParam(required = false)ResignSortField sortField,
                                                            Pageable pageable){
        return ResponseEntity.ok(resignService.getResignList(companyId,keyword,empStatus,sortField, pageable));
    }

//    2.카드 통계
    @GetMapping("/status")
    public ResponseEntity<ResignStatusDto>getResignStatus(@RequestHeader("X-User-Company")UUID companyId){
        return ResponseEntity.ok(resignService.getStatus(companyId));
    }
//
//    3. 상세조회
    @GetMapping("/{resignId}/process")
    public ResponseEntity<ResignDetailDto>getResignDetail(@RequestHeader("X-User-Company")UUID companyId,
                                                          @PathVariable Long resignId){
        return ResponseEntity.ok(resignService.getResignDetail(companyId,resignId));

    }

//    4. 퇴직처리(결재완료건 중 재직->퇴직)
    @PutMapping("/{resignId}")
    public ResponseEntity<Void>processResign(@RequestHeader("X-User-CompanyId")UUID companyId,
                                            @PathVariable Long resignId){
        resignService.processResign(companyId, resignId);
        return ResponseEntity.ok().build();
    }
//
//    5. 삭제(퇴직완료건만 softDelete)
    @DeleteMapping("/{resignId}")
    public ResponseEntity<Void>deleteResign(@RequestHeader("X-User-CompanyId")UUID companyId,
                                            @PathVariable Long resignId){
        resignService.deleteResign(companyId, resignId);
        return ResponseEntity.ok().build();
    }


}
