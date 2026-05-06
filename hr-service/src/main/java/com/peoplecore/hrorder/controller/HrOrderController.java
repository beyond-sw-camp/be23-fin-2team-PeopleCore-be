package com.peoplecore.hrorder.controller;

import com.peoplecore.hrorder.domain.OrderStatus;
import com.peoplecore.hrorder.domain.OrderType;
import com.peoplecore.hrorder.dto.HrOrderCreateReqDto;
import com.peoplecore.hrorder.dto.HrOrderUpdateReqDto;
import com.peoplecore.hrorder.service.HrOrderService;
import jakarta.validation.Valid;
import jakarta.ws.rs.Path;
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
    public ResponseEntity<?> list(@RequestHeader("X-User-Company") UUID companyId,
                                  @RequestParam(required = false) String keyword,
                                  @RequestParam(required = false) OrderType orderType,
                                  @RequestParam(required = false) OrderStatus status,
                                  Pageable pageable) {
        return ResponseEntity.ok(hrOrderService.list(companyId, keyword, orderType, status, pageable));
    }

    // 2. 발령 등록 (status = SCHEDULED, 발령일이 오늘 이전이면 즉시 반영)
    @PostMapping
    public ResponseEntity<?> create(@RequestHeader("X-User-Company") UUID companyId,
                                    @RequestHeader("X-User-Id") Long userId,
                                    @RequestBody @Valid HrOrderCreateReqDto req) {
        Long orderId =hrOrderService.create(companyId,userId,req);
        return ResponseEntity.status(HttpStatus.CREATED).body(orderId);
    }

    // 3. 상세 조회
    @GetMapping("/{orderId}")
    public ResponseEntity<?> detail(@RequestHeader("X-User-Company") UUID companyId,
                                    @PathVariable Long orderId) {
        return ResponseEntity.ok(hrOrderService.detail(companyId, orderId));
    }

    // 4. 수정 (SCHEDULED 상태만)
    @PutMapping("/{orderId}")
    public ResponseEntity<?> update(@RequestHeader("X-User-Company") UUID companyId,
                                    @PathVariable Long orderId,
                                    @RequestBody @Valid HrOrderUpdateReqDto req) {
        return ResponseEntity.ok(hrOrderService.update(companyId,orderId,req));
    }

    // 5. 삭제 (SCHEDULED 상태만)
    @DeleteMapping("/{orderId}")
    public ResponseEntity<Void> delete(@RequestHeader("X-User-Company") UUID companyId,
                                       @PathVariable Long orderId) {
        hrOrderService.delete(companyId, orderId);
        return ResponseEntity.noContent().build();
    }

    // 6. 발령일 도래 건 일괄 반영 (스케줄러 호출용, SCHEDULED + effectiveDate <= 오늘 -> employee 반영 + APPLIED)
    //    employee 반영 시 본인에게 자동 알림이 발송됨 (HrOrderService.applyOrder 내부)
    @PostMapping("/apply-scheduled")
    public ResponseEntity<Integer> applyScheduled() {
        return ResponseEntity.ok(hrOrderService.applyAllScheduledOrders());
    }


//    사원별 발령 이력조회
    @GetMapping("/history/{empId}")
    public ResponseEntity<?>history(@RequestHeader("X-User-Company")UUID companyId,
                                    @PathVariable Long empId){
        return ResponseEntity.ok(hrOrderService.history(companyId,empId));
    }
}

