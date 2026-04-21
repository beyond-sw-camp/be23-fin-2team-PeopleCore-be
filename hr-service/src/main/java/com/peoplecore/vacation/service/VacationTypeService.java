package com.peoplecore.vacation.service;

import com.peoplecore.company.domain.Company;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.vacation.dto.VacationTypeReorderRequestDto;
import com.peoplecore.vacation.dto.VacationTypeRequest;
import com.peoplecore.vacation.dto.VacationTypeResponse;
import com.peoplecore.vacation.entity.GenderLimit;
import com.peoplecore.vacation.entity.PayType;
import com.peoplecore.vacation.entity.StatutoryVacationType;
import com.peoplecore.vacation.entity.VacationType;
import com.peoplecore.vacation.repository.VacationBalanceRepository;
import com.peoplecore.vacation.repository.VacationRequestRepository;
import com.peoplecore.vacation.repository.VacationTypeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/* 휴가 유형 서비스 - 회사 생성 시 자동 INSERT + 관리자 CRUD */
/* 시스템 예약 유형(StatutoryVacationType enum) 은 생성/수정/삭제 모두 차단 */
@Service
@Slf4j
@Transactional(readOnly = true)
public class VacationTypeService {

    /* 신규 생성 시 sortOrder 기본값 - 관리자가 나중에 순서 조정 */
    private static final int DEFAULT_SORT_ORDER = 999;

    private final VacationTypeRepository vacationTypeRepository;
    private final VacationBalanceRepository vacationBalanceRepository;   // 물리 삭제 참조 체크용
    private final VacationRequestRepository vacationRequestRepository;   // 물리 삭제 참조 체크용

    @Autowired
    public VacationTypeService(VacationTypeRepository vacationTypeRepository,
                               VacationBalanceRepository vacationBalanceRepository,
                               VacationRequestRepository vacationRequestRepository) {
        this.vacationTypeRepository = vacationTypeRepository;
        this.vacationBalanceRepository = vacationBalanceRepository;
        this.vacationRequestRepository = vacationRequestRepository;
    }

    /* 회사 생성 시 시스템 예약 유형 전체 INSERT (멱등) */
    /* 기존 코드 Set 1회 조회 → enum 순회 → 누락된 코드만 saveAll → 법 개정으로 enum 확장 시 기존 회사도 재실행만으로 보충 가능 */
    @Transactional
    public void initDefault(Company company) {
        UUID companyId = company.getCompanyId();

        // 현재 회사 보유 코드 한 번에 조회 (N 쿼리 방지)
        Set<String> existingCodes = vacationTypeRepository
                .findAllByCompanyIdOrderBySortOrderAsc(companyId).stream()
                .map(VacationType::getTypeCode)
                .collect(Collectors.toSet());

        // enum 순회하여 누락된 유형만 필터링
        List<VacationType> toInsert = Arrays.stream(StatutoryVacationType.values())
                .filter(t -> !existingCodes.contains(t.getCode()))
                .map(t -> t.toEntity(companyId))
                .toList();

        if (toInsert.isEmpty()) {
            log.info("VacationType 시스템 예약 모두 존재 - companyId={}, 초기화 스킵", companyId);
            return;
        }
        vacationTypeRepository.saveAll(toInsert);
        log.info("VacationType 시스템 예약 {} 건 INSERT - companyId={}, codes={}",
                toInsert.size(), companyId,
                toInsert.stream().map(VacationType::getTypeCode).toList());
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
        // enum 정의된 예약 코드(월차/연차/법정휴가 전체) 는 관리자가 직접 생성 불가
        if (StatutoryVacationType.isReserved(typeCode)) {
            throw new CustomException(ErrorCode.VACATION_TYPE_SYSTEM_RESERVED);
        }
        if (vacationTypeRepository.existsByCompanyIdAndTypeCode(companyId, typeCode)) {
            throw new CustomException(ErrorCode.VACATION_TYPE_CODE_DUPLICATE);
        }

        Integer sortOrder = request.getSortOrder() != null ? request.getSortOrder() : DEFAULT_SORT_ORDER;
        // 회사 커스텀 유형은 기본 ALL/PAID - 요청에 명시된 경우 덮어쓰기
        GenderLimit genderLimit = request.getGenderLimit() != null ? request.getGenderLimit() : GenderLimit.ALL;
        PayType payType = request.getPayType() != null ? request.getPayType() : PayType.PAID;

        VacationType created = vacationTypeRepository.save(
                VacationType.builder()
                        .companyId(companyId)
                        .typeCode(typeCode)
                        .typeName(request.getTypeName())
                        .deductUnit(request.getDeductUnit())
                        .isActive(true)
                        .sortOrder(sortOrder)
                        .genderLimit(genderLimit)
                        .payType(payType)
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

    /* 일괄 재정렬 - 드래그 앤 드롭 결과 반영. 시스템 예약 유형도 순서 변경 허용 */
    /* 1. 요청 items 비어있으면 no-op */
    /* 2. typeIds 일괄 조회 → 타 회사 소속/존재하지 않는 ID 포함 시 VACATION_TYPE_NOT_FOUND */
    /* 3. typeId → sortOrder map 으로 빠른 매칭 후 엔티티별 updateSortOrder 호출 */
    /* 4. 개별 save 불필요 - 영속성 컨텍스트 dirty checking 으로 TX 커밋 시 일괄 UPDATE */
    @Transactional
    public List<VacationTypeResponse> reorder(UUID companyId, VacationTypeReorderRequestDto request) {
        List<VacationTypeReorderRequestDto.Item> items = request.getItems();
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        // 요청 items → typeId→sortOrder map. 중복 typeId 요청 시 나중 값이 최종값
        Map<Long, Integer> targetOrderMap = new HashMap<>();
        for (VacationTypeReorderRequestDto.Item item : items) {
            if (item.getTypeId() == null || item.getSortOrder() == null) {
                throw new CustomException(ErrorCode.BAD_REQUEST);
            }
            targetOrderMap.put(item.getTypeId(), item.getSortOrder());
        }

        // 회사 소속 + 요청 typeId 에 속하는 엔티티 일괄 조회 (IN 쿼리 1회)
        List<VacationType> types = vacationTypeRepository
                .findAllByCompanyIdAndTypeIdIn(companyId, List.copyOf(targetOrderMap.keySet()));
        if (types.size() != targetOrderMap.size()) {
            // 존재하지 않거나 타 회사 소속인 typeId 가 포함됨 → 일관된 NOT_FOUND 로 통일 (권한 누수 방지)
            log.warn("[VacationType] 재정렬 - 존재하지 않거나 타 회사 typeId 포함. company={}, requested={}, found={}",
                    companyId, targetOrderMap.keySet(), types.stream().map(VacationType::getTypeId).toList());
            throw new CustomException(ErrorCode.VACATION_TYPE_NOT_FOUND);
        }

        // 엔티티별 sortOrder 적용. 시스템 예약 차단 없음 (순서 변경은 허용)
        for (VacationType type : types) {
            type.updateSortOrder(targetOrderMap.get(type.getTypeId()));
        }
        log.info("[VacationType] 일괄 재정렬 - companyId={}, count={}", companyId, types.size());

        // 응답은 sortOrder 적용 후 재정렬된 전체 목록 반환 (프론트가 즉시 재렌더 가능)
        return vacationTypeRepository.findAllByCompanyIdOrderBySortOrderAsc(companyId)
                .stream()
                .map(VacationTypeResponse::from)
                .toList();
    }

    /* 물리 삭제 - 완전한 DELETE. 잔여/신청 이력 0 건일 때만 허용 */
    /* 1. 시스템 예약(enum) 차단 → VACATION_TYPE_SYSTEM_RESERVED */
    /* 2. VacationBalance / VacationRequest 참조 존재 시 차단 → VACATION_TYPE_IN_USE */
    /* 3. 참조 없음 확인 후 DELETE - 오타로 생성한 유형 즉시 제거 용도 */
    @Transactional
    public void hardDelete(UUID companyId, Long typeId) {
        VacationType type = loadWithCompanyCheck(companyId, typeId);
        if (type.isSystemReserved()) {
            throw new CustomException(ErrorCode.VACATION_TYPE_SYSTEM_RESERVED);
        }
        // FK 참조 검사 - 두 테이블 중 하나라도 걸리면 차단 (과거 이력 보존)
        if (vacationBalanceRepository.existsByVacationType_TypeId(typeId)
                || vacationRequestRepository.existsByVacationType_TypeId(typeId)) {
            throw new CustomException(ErrorCode.VACATION_TYPE_IN_USE);
        }
        vacationTypeRepository.delete(type);
        log.info("[VacationType] 물리 삭제 - companyId={}, typeId={}, code={}",
                companyId, typeId, type.getTypeCode());
    }

    /* typeId 조회 + 회사 소속 검증 - 다른 회사 유형 조작 방지 */
    /* 1. typeId 로 조회, 없으면 VACATION_TYPE_NOT_FOUND */
    /* 2. 회사 불일치 시 WARN 로그 + 동일 NOT_FOUND 예외 (존재 여부 노출 방지) */
    /* 예외: 존재 안 함 / 타 회사 접근 모두 VACATION_TYPE_NOT_FOUND 로 통일 (권한 누수 방지) */
    private VacationType loadWithCompanyCheck(UUID companyId, Long typeId) {
        VacationType type = vacationTypeRepository.findById(typeId)
                .orElseThrow(() -> new CustomException(ErrorCode.VACATION_TYPE_NOT_FOUND));
        if (!type.getCompanyId().equals(companyId)) {
            log.warn("[VacationType] 타 회사 typeId 접근 차단 - requestCompany={}, typeId={}, ownerCompany={}",
                    companyId, typeId, type.getCompanyId());
            throw new CustomException(ErrorCode.VACATION_TYPE_NOT_FOUND);
        }
        return type;
    }

}