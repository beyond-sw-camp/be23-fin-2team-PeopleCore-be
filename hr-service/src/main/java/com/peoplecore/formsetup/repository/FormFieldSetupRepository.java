package com.peoplecore.formsetup.repository;

import com.peoplecore.formsetup.domain.FormFieldSetup;
import com.peoplecore.formsetup.domain.FormType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;
import java.util.UUID;

public interface FormFieldSetupRepository extends JpaRepository<FormFieldSetup, Long> {
    // 회사별 + 폼타입별 폼 조회 (순서 정렬)
    List<FormFieldSetup> findAllByCompanyIdAndFormTypeOrderBySectionAscSortOrderAsc(UUID companyId, FormType formType);

    // 회사별 + 폼타입별 기존 기록 삭제 (일괄 저장 시 사용)
    @Modifying
    void deleteAllByCompanyIdAndFormType(UUID companyId, FormType formType);

    // 회사별 + 폼타입별 설정 존재 여부 (기본값 자동 생성 판단용)
    boolean existsByCompanyIdAndFormType(UUID companyId, FormType formType);
}
