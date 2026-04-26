package com.peoplecore.title.repository;

import com.peoplecore.title.domain.Title;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TitleRepository extends JpaRepository<Title, Long> {
    List<Title> findAllByCompanyId(UUID companyId);
    boolean existsByTitleNameAndCompanyIdAndDeptId(String titleName, UUID companyId, Long deptId);
    long countByCompanyId(UUID companyId);

    boolean existsByTitleNameAndCompanyIdAndDeptIdAndTitleIdNot(
            String titleName, UUID companyId, Long deptId, Long titleId);

    Optional<Title> findTopByCompanyIdOrderByTitleCodeDesc(UUID companyId);
    Optional<Title> findByCompanyIdAndTitleName(UUID companyId, String titleName);


    Optional<Title> findByTitleName(String titleName);

    // id 로 직책 조회, 회사 스코프 동시 검증 (테넌트 우회 방지)
    Optional<Title> findByTitleIdAndCompanyId(Long titleId, UUID companyId);
}
