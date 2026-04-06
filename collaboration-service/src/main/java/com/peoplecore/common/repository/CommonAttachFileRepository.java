package com.peoplecore.common.repository;

import com.peoplecore.common.entity.CommonAttachFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CommonAttachFileRepository extends JpaRepository<CommonAttachFile, Long> {
    /*entity 타입 +  entityId로 단건 조회 */
    Optional<CommonAttachFile> findByCompanyIdAndEntityTypeAndEntityId(UUID companyId, String entityType, Long entityId);

    /*entity 타입 + entityId로 삭제 */
    @Modifying(clearAutomatically = true)
    void deleteByCompanyIdAndEntityTypeAndEntityId(UUID companyId, String entityType, Long entityId);
}
