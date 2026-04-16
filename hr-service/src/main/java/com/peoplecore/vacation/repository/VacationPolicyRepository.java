package com.peoplecore.vacation.repository;

import com.peoplecore.vacation.entity.VacationPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VacationPolicyRepository extends JpaRepository<VacationPolicy, Long> {
    /*
     * 회사별 정책 단건 조회
     * 용도: 연차 발생 잡, 촉진 통지 잡, 화면 정책 조회
     * 인덱스: uk_vacation_policy_company (커버)
     * 반환: Optional - 회사 생성 시 자동 INSERT 되므로 정상 케이스는 항상 존재
     */
    Optional<VacationPolicy> findByCompanyId(UUID companyId);

    /*
     * 회사별 정책 + 발생 규칙 fetch join
     * 용도: 연차 발생 잡 (정책 + 규칙 11건 한 쿼리로 로드, N+1 방지)
     * 반환: Optional. grantRules 컬렉션 초기화 보장
     */
    @Query("""
            SELECT p FROM VacationPolicy p
            LEFT JOIN FETCH p.grantRules
             WHERE p.companyId = :companyId
            """)
    Optional<VacationPolicy> findByCompanyIdFetchRules(@Param("companyId") UUID companyId);

    /*
     * 정책 존재 여부 (initDefault 멱등성 보장용)
     * 용도: 회사 생성 시점에 이미 정책이 있는지 체크 → 중복 INSERT 방지
     */
    boolean existsByCompanyId(UUID companyId);
}



