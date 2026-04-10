package com.peoplecore.hrorder.controller;

import com.peoplecore.hrorder.domain.OrderStatus;
import com.peoplecore.hrorder.domain.OrderType;
import com.peoplecore.hrorder.service.HrOrderService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/hr-order")
public class HrOrderController {

    private final HrOrderService hrOrderService;

    public HrOrderController(HrOrderService hrOrderService) {
        this.hrOrderService = hrOrderService;
    }

    // 1. 목록 조회 (검색/필터/정렬/페이지네이션)
    @GetMapping
    public ResponseEntity<?> list(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) OrderType orderType,
            @RequestParam(required = false) OrderStatus status,
            Pageable pageable) {
        return ResponseEntity.ok(hrOrderService.list(companyId, keyword, orderType, status, pageable));
    }

    // 2. 발령 등록 (status = PENDING)
    @PostMapping
    public ResponseEntity<?> create(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody @Valid Object req) { // TODO: HrOrderCreateReqDto
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    // 3. 상세 조회
    @GetMapping("/{orderId}")
    public ResponseEntity<?> detail(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long orderId) {
        return ResponseEntity.ok(hrOrderService.detail(companyId, orderId));
    }

    // 4. 수정 (PENDING 상태만)
    @PutMapping("/{orderId}")
    public ResponseEntity<?> update(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long orderId,
            @RequestBody @Valid Object req) { // TODO: HrOrderUpdateReqDto
        return ResponseEntity.ok().build();
    }

    // 5. 삭제 (PENDING 상태만)
    @DeleteMapping("/{orderId}")
    public ResponseEntity<Void> delete(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long orderId) {
        hrOrderService.delete(companyId, orderId);
        return ResponseEntity.noContent().build();
    }

    // 6. 승인 (PENDING -> CONFIRMED, HR_SUPER_ADMIN만)
    @PutMapping("/{orderId}/confirm")
    public ResponseEntity<Void> confirm(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable Long orderId) {
        if (!"HR_SUPER_ADMIN".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        hrOrderService.confirm(companyId, orderId);
        return ResponseEntity.ok().build();
    }

    // 7. 반려 (PENDING → REJECTED, HR_SUPER_ADMIN만)
    @PutMapping("/{orderId}/reject")
    public ResponseEntity<Void> reject(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable Long orderId) {
        if (!"HR_SUPER_ADMIN".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        hrOrderService.reject(companyId, orderId);
        return ResponseEntity.ok().build();
    }

    // 8. 통보 (CONFIRMED 상태만)
    @PutMapping("/{orderId}/notify")
    public ResponseEntity<Void> notifyOrder(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long orderId) {
        hrOrderService.notifyOrder(companyId, orderId);
        return ResponseEntity.ok().build();
    }

    // 9. 발령일 도래 건 일괄 반영 (스케줄러 호출용, CONFIRMED + effectiveDate <= 오늘 -> employee 반영 + APPLIED)
    @PostMapping("/apply-scheduled")
    public ResponseEntity<Integer> applyScheduled() {
        return ResponseEntity.ok(hrOrderService.applyAllScheduledOrders());
    }
}

