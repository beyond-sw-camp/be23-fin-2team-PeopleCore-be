package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.PayItems;
import com.peoplecore.pay.enums.PayItemType;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayItemsRepository extends JpaRepository<PayItems, Long> {

    Optional<PayItems> findByPayItemIdAndCompany_CompanyId(Long payItemId, UUID companyId);

    // 다중 삭제 전 존재 확인
    List<PayItems> findByPayItemIdInAndCompany_CompanyId(List<Long> payItemIds, UUID companyId);

    List<PayItems>findByCompany_CompanyIdAndPayItemTypeAndIsActiveTrueOrderBySortOrderAsc(UUID companyId, PayItemType payItemType);


//    관리화면 - 삭제되지 않은 항목만 조회(삭제X, 비활성O,X)
}
