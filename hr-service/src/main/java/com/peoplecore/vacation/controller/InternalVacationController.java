package com.peoplecore.vacation.controller;

import com.peoplecore.vacation.dto.VacationValidateRequestDto;
import com.peoplecore.vacation.service.VacationRequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/* 내부 서비스 간 호출 전용 - collab RestClient(HrServiceClient) 진입점 */
@RestController
@RequestMapping("/internal/vacation")
public class InternalVacationController {

    private final VacationRequestService vacationRequestService;

    @Autowired
    public InternalVacationController(VacationRequestService vacationRequestService) {
        this.vacationRequestService = vacationRequestService;
    }

    /* 휴가 신청 사전 검증 - 결재 문서 생성 전 collab 가 동기 호출 */
    /* 통과 시 200, 실패 시 CustomException(ErrorCode) → 글로벌 핸들러가 4xx 응답 */
    /* 검증: items 비어있음 / 사원·유형 존재 / 성별 제한 / 미리쓰기 정책 + 잔여 부족 */
    @PostMapping("/validate-request")
    public ResponseEntity<Void> validateRequest(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestBody VacationValidateRequestDto request) {
        vacationRequestService.validateForCreate(
                companyId, request.getEmpId(), request.getInfoId(), request.getItems());
        return ResponseEntity.ok().build();
    }
}
