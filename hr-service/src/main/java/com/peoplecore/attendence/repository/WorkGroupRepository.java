package com.peoplecore.attendence.repository;

import com.peoplecore.attendence.entity.WorkGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import javax.swing.text.html.Option;
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
}
