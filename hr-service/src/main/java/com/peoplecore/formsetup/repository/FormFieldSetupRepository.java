package com.peoplecore.formsetup.repository;

import com.peoplecore.formsetup.domain.FormFieldSetup;
import com.peoplecore.formsetup.domain.FormType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;
import java.util.UUID;

public interface FormFieldSetupRepository extends JpaRepository<FormFieldSetup, Long> {
    // 회사별 + 폼타입별 폼 조회 (순서 정렬)
    // company 엔티티의 companyId로 조회 (Company company → company_companyId)
    List<FormFieldSetup> findAllByCompany_CompanyIdAndFormTypeOrderBySectionAscSortOrderAsc(UUID companyId, FormType formType);

    @Modifying
    void deleteAllByCompany_CompanyIdAndFormType(UUID companyId, FormType formType);

    boolean existsByCompany_CompanyIdAndFormType(UUID companyId, FormType formType);

}
