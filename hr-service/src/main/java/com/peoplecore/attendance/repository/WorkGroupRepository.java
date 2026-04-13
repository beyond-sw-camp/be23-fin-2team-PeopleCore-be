package com.peoplecore.attendance.repository;

import com.peoplecore.attendance.dto.WorkGroupOptionResDto;
import com.peoplecore.attendance.entity.WorkGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkGroupRepository extends JpaRepository<WorkGroup, Long> {
    /* 회사별 근무 그룹 목록 (삭제 X) */
    List<WorkGroup> findByCompany_CompanyIdAndGroupDeleteAtIsNull(UUID companyId);

    /*단일 근무 그룹 조회 */
    Optional<WorkGroup> findByWorkGroupIdAndGroupDeleteAtIsNull(Long workGroupId);

    /*근무 그룹 코드 중복 체크 */
    boolean existsByCompany_CompanyIdAndGroupCodeAndGroupDeleteAtIsNull(UUID companyId, String groupCode);

    /* 회사 기본 근무 그룹 조회 */
    Optional<WorkGroup> findByCompany_CompanyIdAndGroupCodeAndGroupDeleteAtIsNull(UUID companyID, String groupCode);

    /* 사원 생성 시 드롭다운 용 근무 그룹 옵션 */
    @Query("SELECT new com.peoplecore.attendance.dto.WorkGroupOptionResDto(" +
            "  w.workGroupId, w.groupName, w.groupCode) " +
            "FROM WorkGroup w " +
            "WHERE w.company.companyId = :companyId " +
            "  AND w.groupDeleteAt IS NULL " +
            "ORDER BY w.groupName ASC")
    List<WorkGroupOptionResDto> findOptionsByCompanyId(@Param("companyId") UUID companyId);
}
