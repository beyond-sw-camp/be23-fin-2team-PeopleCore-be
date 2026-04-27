package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.PayItems;
import com.peoplecore.pay.enums.LegalCalcType;
import com.peoplecore.pay.enums.PayItemCategory;
import com.peoplecore.pay.enums.PayItemType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayItemsRepository extends JpaRepository<PayItems, Long> {

    Optional<PayItems> findByPayItemIdAndCompany_CompanyId(Long payItemId, UUID companyId);

    // 다중 삭제 전 존재 확인
    List<PayItems> findByPayItemIdInAndCompany_CompanyId(List<Long> payItemIds, UUID companyId);

    List<PayItems>findByCompany_CompanyIdAndPayItemTypeAndIsActiveTrueOrderBySortOrderAsc(UUID companyId, PayItemType payItemType);

    // 연봉계약서 폼 급여 섹션 전용 — 지급항목 중 비법정 + 급여/수당/상여 카테고리만 필터링
    @Query("""
        SELECT p FROM PayItems p
        WHERE p.company.companyId = :companyId
          AND p.payItemType = com.peoplecore.pay.enums.PayItemType.PAYMENT
          AND p.isActive = true
          AND (p.isLegal = false OR p.isLegal IS NULL)
          AND p.payItemCategory IN (
              com.peoplecore.pay.enums.PayItemCategory.SALARY,
              com.peoplecore.pay.enums.PayItemCategory.ALLOWANCE,
              com.peoplecore.pay.enums.PayItemCategory.BONUS
          )
        ORDER BY p.sortOrder ASC
    """)
    List<PayItems> findActiveNonLegalPaymentItems(@Param("companyId") UUID companyId);

    List<PayItems> findByCompany_CompanyIdAndPayItemTypeAndPayItemNameIn(UUID companyId, PayItemType payItemType, List<String> payItemNames);


//    정산전용 PayItems 조회 (isSystem=true인 항목만)
    List<PayItems> findByCompany_CompanyIdAndPayItemNameInAndIsSystemTrue(UUID companyId, List<String> payItemNames);

//    법정 항목 조회
     Optional<PayItems> findByCompany_CompanyIdAndIsLegalTrueAndLegalCalcType(UUID companyId, LegalCalcType legalCalcType);

}
