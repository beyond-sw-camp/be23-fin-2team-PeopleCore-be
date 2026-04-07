package com.peoplecore.approval.repository;

import com.peoplecore.approval.entity.ApprovalForm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApprovalFormRepository extends JpaRepository<ApprovalForm, Long> {

    // 전체 양식 목록
    @Query("SELECT f FROM ApprovalForm f " +
            "JOIN FETCH f.folderId folder " +
            "WHERE f.companyId = :companyId " +
            "AND f.isActive = true AND f.isCurrent = true " +
            "ORDER BY f.formSortOrder")
    List<ApprovalForm> findAllWithFolder(@Param("companyId") UUID companyId);

    // 폴더별 필터
    @Query("SELECT f FROM ApprovalForm f " +
            "JOIN FETCH f.folderId folder " +
            "WHERE f.companyId = :companyId " +
            "AND folder.folderId = :folderId " +
            "AND f.isActive = true AND f.isCurrent = true " +
            "ORDER BY f.formSortOrder")
    List<ApprovalForm> findAllWithFolderByFolderId(
            @Param("companyId") UUID companyId, @Param("folderId") Long folderId);

    // 상세 조회 (단건이라 N+1은 아니지만 통일성 위해)
    @Query("SELECT f FROM ApprovalForm f " +
            "JOIN FETCH f.folderId folder " +
            "WHERE f.formId = :formId " +
            "AND f.companyId = :companyId " +
            "AND f.isActive = true AND f.isCurrent = true")
    Optional<ApprovalForm> findDetailById(
            @Param("formId") Long formId, @Param("companyId") UUID companyId);

    // 폴더 내 최대 정렬순서
    @Query("SELECT COALESCE(MAX(f.formSortOrder), 0) FROM ApprovalForm f " +
            "WHERE f.companyId = :companyId AND f.folderId.folderId = :folderId")
    Integer findMaxSortOrderInFolder(@Param("companyId") UUID companyId, @Param("folderId") Long folderId);

    // 양식코드 중복 체크
    boolean existsByCompanyIdAndFormCode(UUID companyId, String formCode);

    // 양식명 중복 체크
    boolean existsByCompanyIdAndFormName(UUID companyId, String formName);

    // 여러 양식 ID로 조회 (일괄설정용)
    @Query("SELECT f FROM ApprovalForm f WHERE f.companyId = :companyId AND f.formId IN :formIds")
    List<ApprovalForm> findAllByCompanyIdAndFormIds(@Param("companyId") UUID companyId, @Param("formIds") List<Long> formIds);

    /* 양식 정렬 순서 변경 벌크쿼리*/
    @Modifying
    @Query("UPDATE ApprovalForm f SET f.formSortOrder = :sortOrder " +
            "WHERE f.formId = :formId AND f.companyId = :companyId")
    void updateSortOrder(@Param("companyId") UUID companyId,
                         @Param("formId") Long formId,
                         @Param("sortOrder") Integer sortOrder);
}
