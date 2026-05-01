package com.peoplecore.approval.repository;

import com.peoplecore.approval.entity.ApprovalForm;
import com.peoplecore.approval.entity.FrequentForm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FrequentFormRepository extends JpaRepository<FrequentForm, Long> {
    /* 사원별 자주 쓰는 양식 목록 — 활성·현재·미삭제 양식 + 미삭제 폴더만 */
    @Query("SELECT ff FROM FrequentForm ff " +
            "JOIN FETCH ff.form f " +
            "JOIN FETCH f.folderId folder " +
            "WHERE ff.companyId = :companyId AND ff.empId = :empId " +
            "AND f.isActive = true AND f.isCurrent = true AND f.isDeleted = false " +
            "AND folder.isDeleted = false " +
            "ORDER BY ff.createdAt DESC")
    List<FrequentForm> findAllWithForm(@Param("companyId") UUID companyId, @Param("empId") Long empId);

    boolean existsByCompanyIdAndEmpIdAndForm_FormId(UUID companyId, Long empId, Long formId);

    Optional<FrequentForm> findByCompanyIdAndEmpIdAndForm_FormId(UUID companyId, Long empId, Long formId);

    /* 즐겨찾기 마이그레이션 — 옛 row 가리키던 즐겨찾기를 새 current row 로 이전. 양식 수정/롤백 시 호출 */
    @Modifying
    @Query("UPDATE FrequentForm ff SET ff.form = :newForm WHERE ff.form.formId = :oldFormId")
    int migrateFormReference(@Param("oldFormId") Long oldFormId,
                             @Param("newForm") ApprovalForm newForm);
}
