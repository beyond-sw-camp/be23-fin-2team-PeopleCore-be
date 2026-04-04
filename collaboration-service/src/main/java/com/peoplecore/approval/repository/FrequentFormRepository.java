package com.peoplecore.approval.repository;

import com.peoplecore.approval.entity.FrequentForm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FrequentFormRepository extends JpaRepository<FrequentForm, Long> {
    @Query("SELECT ff FROM FrequentForm ff " +
            "JOIN FETCH ff.form f " +
            "JOIN FETCH f.folderId folder " +
            "WHERE ff.companyId = :companyId AND ff.empId = :empId " +
            "AND f.isActive = true AND f.isCurrent = true " +
            "ORDER BY ff.createdAt DESC")
    List<FrequentForm> findAllWithForm(@Param("companyId") UUID companyId, @Param("empId") Long empId);

    boolean existsByCompanyIdAndEmpIdAndForm_FormId(UUID companyId, Long empId, Long formId);

    Optional<FrequentForm> findByCompanyIdAndEmpIdAndForm_FormId(UUID companyId, Long empId, Long formId);

}
