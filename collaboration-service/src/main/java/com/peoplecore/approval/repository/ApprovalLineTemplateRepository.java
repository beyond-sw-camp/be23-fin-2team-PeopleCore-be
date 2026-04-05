package com.peoplecore.approval.repository;

import com.peoplecore.approval.entity.ApprovalLineTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApprovalLineTemplateRepository extends JpaRepository<ApprovalLineTemplate, Long> {
    @Query("SELECT DISTINCT t FROM ApprovalLineTemplate t " +
            "LEFT JOIN FETCH t.items " +
            "WHERE t.companyId = :companyId AND t.lineTemEmpId = :empId " +
            "ORDER BY t.createdAt DESC")
    List<ApprovalLineTemplate> findWithItemsByCompanyIdAndEmpId(
            @Param("companyId") UUID companyId, @Param("empId") Long empId);

    Optional<ApprovalLineTemplate> findByLineTemIdAndCompanyIdAndLineTemEmpId(Long lineTemId, UUID companyId, Long empId);

    Optional<ApprovalLineTemplate> findByCompanyIdAndLineTemEmpIdAndIsDefaultTrue(UUID companyId, Long empId);


    @Query("SELECT t FROM ApprovalLineTemplate t " +
            "LEFT JOIN FETCH t.items " +
            "WHERE t.companyId = :companyId AND t.lineTemEmpId = :empId " +
            "AND t.isDefault = true")
    Optional<ApprovalLineTemplate> findDefaultWithItems(
            @Param("companyId") UUID companyId, @Param("empId") Long empId);

}
