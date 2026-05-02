package com.peoplecore.pay.controller;

import com.peoplecore.auth.RoleRequired;
import com.peoplecore.pay.dtos.PayItemReqDto;
import com.peoplecore.pay.dtos.PayItemResDto;
import com.peoplecore.pay.enums.PayItemType;
import com.peoplecore.pay.service.PayItemsService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/pay/superadmin/payitems")
@RoleRequired({"HR_SUPER_ADMIN"})
public class PayItemController {

    private final PayItemsService payItemsService;

    @Autowired
    public PayItemController(PayItemsService payItemsService) {
        this.payItemsService = payItemsService;
    }

//    지급/공제 항목 조회(공용)
    @GetMapping
    @RoleRequired({"HR_SUPER_ADMIN", "HR_ADMIN"})
    public ResponseEntity<List<PayItemResDto>> getPayItems(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestParam PayItemType type,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Boolean isLegal){
        return ResponseEntity.ok(payItemsService.getPayItems(companyId, type, name, isLegal));
    }

//    항목 추가
    @PostMapping
    public ResponseEntity<PayItemResDto> createPayItem(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestBody @Valid PayItemReqDto reqDto){
        return ResponseEntity.status(HttpStatus.CREATED).body(payItemsService.createPayItem(companyId, reqDto));
    }

//    항목 수정
    @PutMapping("/{payItemId}")
    public ResponseEntity<PayItemResDto> updatePayItem(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long payItemId,
            @RequestBody @Valid PayItemReqDto reqDto){
        return ResponseEntity.ok(payItemsService.updatePayItem(companyId, payItemId, reqDto));
    }

//    항목 사용여부 토글 (수정)
    @PatchMapping("/{payItemId}")
    public ResponseEntity<PayItemResDto> toggleStatus(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long payItemId){
        return ResponseEntity.ok(payItemsService.toggleStatus(companyId, payItemId));
    }

//    항목 삭제 (다중삭제)
    @DeleteMapping
    public ResponseEntity<Void> deletePayItems(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestBody List<Long> payItemIds){
        payItemsService.deletePayItems(companyId, payItemIds);
        return ResponseEntity.noContent().build();
    }


}
