package com.peoplecore.hrorder.repository;

import com.peoplecore.hrorder.domain.HrOrder;
import com.peoplecore.hrorder.domain.HrOrderDetail;
import com.peoplecore.hrorder.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HrOrderRepository extends JpaRepository<HrOrder, Long>,HrOrderRepositoryCustom {

//    상세조회(발령+사원)
    @Query("""
            SELECT o FROM HrOrder o
            JOIN FETCH o.employee e
            WHERE o.orderId = :orderId
            AND e.company.companyId = :companyId
            AND o.deletedAt IS NULL
            """)
    Optional<HrOrder> findByOrderIdAndCompanyId (@Param("orderId")Long orderId,
                                                 @Param("companyId") UUID companyId);

//    변경 상세목록조회
    List<HrOrderDetail>findByOrderId(Long orderId);


//    스케줄러(승인 중 발령일)
    List<HrOrder> findByStatusAndEffectiveDateLessThanEqual(OrderStatus status, LocalDate date);

//    사원별 발령이력 조회(APPLIED상태, 최신순)
    @Query("""
SELECT o FROM HrOrder o
JOIN FETCH o.employee e
WHERE e.empId = :empId
AND e.company.companyId = :companyId
AND o.deletedAt IS NULL
ORDER BY o.effectiveDate DESC
""")
    List<HrOrder>findHistoryByEmpId(@Param("companyId")UUID companyId,
                                    @Param("empId")Long empId);
}
