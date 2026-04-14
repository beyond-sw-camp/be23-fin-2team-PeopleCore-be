package com.peoplecore.attendance.repository;

import com.peoplecore.attendance.entity.CompanyAllowedIp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyAllowedIpRepository extends JpaRepository<CompanyAllowedIp, Long> {
    /** 회사별 전체 목록 (활성+비활성 포함, 관리 화면용) */
    List<CompanyAllowedIp> findByCompany_CompanyIdOrderByIdAsc(UUID companyId);

    /** 회사별 활성 대역만 - 근태 판정(매칭)에 사용 */
    List<CompanyAllowedIp> findByCompany_CompanyIdAndIsActiveTrue(UUID companyId);

    /** 중복 등록 방지용 - (회사, CIDR) 중복 체크 */
    boolean existsByCompany_CompanyIdAndIpCidr(UUID companyId, String ipCidr);

    /** 수정/삭제 시 소유권 검증: 내 회사의 레코드인지 함께 확인 */
    Optional<CompanyAllowedIp> findByIdAndCompany_CompanyId(Long id, UUID companyId);

}
