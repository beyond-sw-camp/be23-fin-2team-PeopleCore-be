package com.peoplecore.common.repository;

import com.peoplecore.common.entity.CommonCodeGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CommonCodeGroupRepository extends JpaRepository<CommonCodeGroup, Long> {

    Optional<CommonCodeGroup> findByCompanyIdAndGroupCodeAndIsActiveTrue(UUID companyId, String groupCode);
}
