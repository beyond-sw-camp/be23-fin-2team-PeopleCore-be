package com.peoplecore.capability.repository;

import com.peoplecore.capability.entity.TitleCapability;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TitleCapabilityRepository extends JpaRepository<TitleCapability, Long> {

    List<TitleCapability> findByTitleId(Long titleId);

    List<TitleCapability> findByCompanyId(UUID companyId);

    boolean existsByTitleIdAndCapabilityCode(Long titleId, String capabilityCode);

    void deleteByTitleIdAndCapabilityCode(Long titleId, String capabilityCode);
}
