package com.peoplecore.vacation.service;

import com.peoplecore.company.domain.Company;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.vacation.dto.VacationTypeRequest;
import com.peoplecore.vacation.dto.VacationTypeResponse;
import com.peoplecore.vacation.entity.VacationType;
import com.peoplecore.vacation.repository.VacationTypeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/* 휴가 유형 서비스 - 회사 생성 시 자동 INSERT + 관리자 CRUD */
/* 시스템 예약 유형 (MONTHLY/ANNUAL) 은 생성/수정/삭제 모두 차단 */
@Service
@Slf4j
@Transactional(readOnly = true)
public class VacationTypeService {

    /* 신규 생성 시 sortOrder 기본값 - 관리자가 나중에 순서 조정 */
    private static final int DEFAULT_SORT_ORDER = 999;

    private final VacationTypeRepository vacationTypeRepository;

    @Autowired
    public VacationTypeService(VacationTypeRepository vacationTypeRepository) {
        this.vacationTypeRepository = vacationTypeRepository;
    }

    /* 회사 생성 시 시스템 예약 유형 2건 자동 INSERT (멱등) */
    @Transactional
    public void initDefault(Company company) {
        UUID companyId = company.getCompanyId();
        if (vacationTypeRepository.existsByCompanyIdAndTypeCode(companyId, VacationType.CODE_MONTHLY)) {
            log.info("VacationType 시스템 예약 이미 존재 - companyId={}, 초기화 스킵", companyId);
            return;
        }
        vacationTypeRepository.saveAll(List.of(
                VacationType.createDefaultMonthly(companyId),
                VacationType.createDefaultAnnual(companyId)
        ));
        log.info("VacationType 기본 유형 (MONTHLY + ANNUAL) 생성 완료 - companyId={}", companyId);
    }

    /* 활성 유형 목록 - 사원 휴가 신청 드롭다운. sortOrder 오름차순 */
    public List<VacationTypeResponse> listActive(UUID companyId) {
        return vacationTypeRepository
                .findAllByCompanyIdAndIsActiveTrueOrderBySortOrderAsc(companyId)
                .stream()
                .map(VacationTypeResponse::from)
                .toList();
    }

    /* 전체 유형 목록 - 관리자 화면 (비활성 포함) */
    public List<VacationTypeResponse> listAll(UUID companyId) {
        return vacationTypeRepository
                .findAllByCompanyIdOrderBySortOrderAsc(companyId)
                .stream()
                .map(VacationTypeResponse::from)
                .toList();
    }

    /* 신규 유형 생성 - 시스템 예약 코드 차단 + UNIQUE 검증 */
    /* 예외: VACATION_TYPE_SYSTEM_RESERVED, VACATION_TYPE_CODE_DUPLICATE */
    @Transactional
    public VacationTypeResponse create(UUID companyId, VacationTypeRequest request) {
        String typeCode = request.getTypeCode();
        if (VacationType.CODE_MONTHLY.equals(typeCode) || VacationType.CODE_ANNUAL.equals(typeCode)) {
            throw new CustomException(ErrorCode.VACATION_TYPE_SYSTEM_RESERVED);
        }
        if (vacationTypeRepository.existsByCompanyIdAndTypeCode(companyId, typeCode)) {
            throw new CustomException(ErrorCode.VACATION_TYPE_CODE_DUPLICATE);
        }

        Integer sortOrder = request.getSortOrder() != null ? request.getSortOrder() : DEFAULT_SORT_ORDER;
        VacationType created = vacationTypeRepository.save(
                VacationType.builder()
                        .companyId(companyId)
                        .typeCode(typeCode)
                        .typeName(request.getTypeName())
                        .deductUnit(request.getDeductUnit())
                        .isActive(true)
                        .sortOrder(sortOrder)
                        .build()
        );
        log.info("[VacationType] 신규 생성 - companyId={}, typeId={}, code={}",
                companyId, created.getTypeId(), typeCode);
        return VacationTypeResponse.from(created);
    }

    /* 표시 정보 수정 - typeCode 는 불변. 시스템 예약 차단 */
    @Transactional
    public VacationTypeResponse updateDisplay(UUID companyId, Long typeId, VacationTypeRequest request) {
        VacationType type = loadWithCompanyCheck(companyId, typeId);
        if (type.isSystemReserved()) {
            throw new CustomException(ErrorCode.VACATION_TYPE_SYSTEM_RESERVED);
        }
        type.updateDisplay(request.getTypeName(), request.getDeductUnit(), request.getSortOrder());
        log.info("[VacationType] 수정 - typeId={}", typeId);
        return VacationTypeResponse.from(type);
    }

    /* 비활성화 - 시스템 예약 차단. 기존 잔여는 사용 가능 (신규 신청만 차단) */
    @Transactional
    public void deactivate(UUID companyId, Long typeId) {
        VacationType type = loadWithCompanyCheck(companyId, typeId);
        if (type.isSystemReserved()) {
            throw new CustomException(ErrorCode.VACATION_TYPE_SYSTEM_RESERVED);
        }
        type.deactivate();
        log.info("[VacationType] 비활성화 - typeId={}", typeId);
    }

    /* 재활성화 - 시스템 예약은 원래 활성이라 멱등 (차단 안 함) */
    @Transactional
    public void activate(UUID companyId, Long typeId) {
        VacationType type = loadWithCompanyCheck(companyId, typeId);
        type.activate();
        log.info("[VacationType] 활성화 - typeId={}", typeId);
    }

    /* typeId 조회 + 회사 소속 검증 - 다른 회사 유형 조작 방지 */
    /* 타 회사 typeId 접근 시 NOT_FOUND 로 위장 (권한 누수 방지) */
    private VacationType loadWithCompanyCheck(UUID companyId, Long typeId) {
        VacationType type = vacationTypeRepository.findById(typeId)
                .orElseThrow(() -> new CustomException(ErrorCode.VACATION_TYPE_NOT_FOUND));
        if (!type.getCompanyId().equals(companyId)) {
            throw new CustomException(ErrorCode.VACATION_TYPE_NOT_FOUND);
        }
        return type;
    }
}