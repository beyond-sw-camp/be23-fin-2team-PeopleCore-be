package com.peoplecore.common.repository;

import com.peoplecore.common.entity.CommonCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CommonCodeRepository extends JpaRepository<CommonCode, Long> {

    Optional<CommonCode> findByGroupIdAndCodeValueAndIsActiveTrue(Long groupId, String codeValue);

    Optional<CommonCode> findByGroupIdAndCodeValue(Long groupId, String codeValue);

    @Query("SELECT COALESCE(MAX(c.sortOrder), 0) FROM CommonCode c WHERE c.groupId = :groupId")
    Integer findMaxSortOrder(@Param("groupId") Long groupId);
}
