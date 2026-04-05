package com.peoplecore.repository;

import com.peoplecore.entity.Company;
import com.peoplecore.enums.CompanyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, UUID> {

    /**
     * 상태별 회사 목록 조회
     */
    List<Company> findByStatus(CompanyStatus status);

    /**
     * 계약 만료 예정 회사 조회 (알림 대상)
     * - ACTIVE 상태
     * - 만료일이 오늘 + dDay 이내
     * - 아직 해당 D-day 알림을 보내지 않았거나, 더 가까운 알림이 필요한 경우
     */
    @Query("SELECT c FROM Company c " +
           "WHERE c.status = 'ACTIVE' " +
           "AND c.contractEndDate = :targetDate " +
           "AND (c.lastNotifiedDays IS NULL OR c.lastNotifiedDays > :dDay)")
    List<Company> findCompaniesForExpiryNotification(
            @Param("targetDate") LocalDate targetDate,
            @Param("dDay") int dDay);

    /**
     * 오늘 만료되는 회사 조회 (자동 EXPIRED 전환 대상)
     * - ACTIVE 상태
     * - 만료일이 오늘 이하
     */
    @Query("SELECT c FROM Company c " +
           "WHERE c.status = 'ACTIVE' " +
           "AND c.contractEndDate <= :today")
    List<Company> findExpiredCompanies(@Param("today") LocalDate today);

    /**
     * 회사명 중복 체크
     */
    boolean existsByCompanyName(String companyName);
}
