package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.PayItems;
import com.peoplecore.pay.enums.LegalCalcType;
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

    List<PayItems> findByCompany_CompanyIdAndPayItemTypeAndPayItemNameIn(UUID companyId, PayItemType payItemType, List<String> payItemNames);


//    정산전용 PayItems 조회 (isSystem=true인 항목만)
    List<PayItems> findByCompany_CompanyIdAndPayItemNameInAndIsSystemTrue(UUID companyId, List<String> payItemNames);

//    법정 항목 조회
     Optional<PayItems> findByCompany_CompanyIdAndIsLegalTrueAndLegalCalcType(UUID companyId, LegalCalcType legalCalcType);
}
