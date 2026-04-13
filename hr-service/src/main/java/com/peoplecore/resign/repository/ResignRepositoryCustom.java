package com.peoplecore.resign.repository;

import com.peoplecore.resign.domain.Resign;
import com.peoplecore.resign.domain.ResignSortField;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ResignRepositoryCustom {

    Page<Resign> findAllWithFilter(UUID companyId, String keyword, String empStatus, ResignSortField sortField, Pageable pageable);
}

