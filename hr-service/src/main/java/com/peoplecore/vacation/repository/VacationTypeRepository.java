package com.peoplecore.vacation.repository;

import com.peoplecore.vacation.entity.VacationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/* 휴가 유형 마스터 레포 */
@Repository
public interface VacationTypeRepository extends JpaRepository<VacationType, Long> {

    /*
     * 회사 + typeCode 단건 조회
     * 용도: 시스템 예약 유형 조회 (MONTHLY/ANNUAL), 스케줄러가 type 식별 시 사용
     * 인덱스: uk_vacation_type_company_code (커버)
     * 반환: Optional - 회사가 유형 비활성화/삭제했으면 empty
     */
    Optional<VacationType> findByCompanyIdAndTypeCode(UUID companyId, String typeCode);

    /*
     * 회사 활성 유형 정렬 목록
     * 용도: 사원 휴가 신청 화면 드롭다운 (활성 유형만 노출)
     * 정렬: sortOrder 오름차순
     * 인덱스: idx_vacation_type_company_active (커버)
     */
    List<VacationType> findAllByCompanyIdAndIsActiveTrueOrderBySortOrderAsc(UUID companyId);

    /*
     * 회사 전체 유형 정렬 목록 (비활성 포함)
     * 용도: 관리자 휴가 유형 관리 화면
     * 정렬: sortOrder 오름차순
     */
    List<VacationType> findAllByCompanyIdOrderBySortOrderAsc(UUID companyId);

    /*
     * 회사 내 typeCode 중복 체크
     * 용도: 관리자가 새 유형 만들 때 중복 검증 (시스템 예약 코드 차단)
     * 반환: true 면 같은 코드 존재 → 생성 차단
     */
    boolean existsByCompanyIdAndTypeCode(UUID companyId, String typeCode);

    /*
     * 회사 + typeId 목록 일괄 조회 - 일괄 재정렬 시 타 회사 소속 유형 섞여있는지 검증 용
     * 용도: 드래그 앤 드롭 재정렬 API. 요청된 typeId 전부가 같은 회사 소속인지 체크
     * 반환 size 와 요청 size 불일치 시 → 타 회사 유형 포함 or 존재하지 않는 ID
     */
    List<VacationType> findAllByCompanyIdAndTypeIdIn(UUID companyId, List<Long> typeIds);
}