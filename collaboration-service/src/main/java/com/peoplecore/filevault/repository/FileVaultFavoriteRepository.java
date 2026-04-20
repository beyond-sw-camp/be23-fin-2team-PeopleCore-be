package com.peoplecore.filevault.repository;

import com.peoplecore.filevault.entity.FavoriteTargetType;
import com.peoplecore.filevault.entity.FileVaultFavorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FileVaultFavoriteRepository extends JpaRepository<FileVaultFavorite, Long> {

    Optional<FileVaultFavorite> findByEmpIdAndTargetTypeAndTargetId(
        Long empId, FavoriteTargetType targetType, Long targetId);

    List<FileVaultFavorite> findByEmpIdAndTargetType(Long empId, FavoriteTargetType targetType);

    List<FileVaultFavorite> findByEmpIdAndTargetTypeAndTargetIdIn(
        Long empId, FavoriteTargetType targetType, List<Long> targetIds);
}
