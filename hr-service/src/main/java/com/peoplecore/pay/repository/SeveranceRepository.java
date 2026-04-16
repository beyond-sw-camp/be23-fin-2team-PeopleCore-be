package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.SeverancePays;
import com.peoplecore.pay.enums.SevStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SeveranceRepository extends JpaRepository<SeverancePays, Long>, SeveranceRepositoryCustom {

    Optional<SeverancePays> findByEmployee_EmpIdAndCompany_CompanyId(Long empId, UUID companyId);


//    회사별 퇴직금 목록 (페이징)
    Page<SeverancePays> findByCompany_CompanyIdAndSevStatus(UUID companyId, SevStatus sevStatus, Pageable pageable);

//    회사별 전체 목록
    Page<SeverancePays> findByCompany_CompanyId(UUID companyId, Pageable pageable);

//    단건 조회 (회사 검증 포함)
    Optional<SeverancePays> findBySevIdAndCompany_CompanyId(Long sevId, UUID companyId);

//    사원별 퇴직금 존재 여부
    boolean existsByEmployee_EmpIdAndCompany_CompanyId(Long empId, UUID companyId );

//    승인 완료 건 목록(지급 처리용) - Pageable 없이 List 반환
    List<SeverancePays> findAllByCompany_CompanyIdAndSevStatus(UUID companyId, SevStatus sevStatus);

//    상태별 건수 집계
    @Query("SELECT s.sevStatus, COUNT(s) FROM SeverancePays s " +
            "WHERE s.company.companyId = :companyId " +
            "GROUP BY s.sevStatus")
    List<Object[]> countBySevStatus(@Param("companyId") UUID companyId);

//    approvalDocId 로 조회 (kafka consumer용)
    Optional<SeverancePays> findByApprovalDocIdAndCompany_CompanyId(Long approvalDocId, UUID companyId);


}
