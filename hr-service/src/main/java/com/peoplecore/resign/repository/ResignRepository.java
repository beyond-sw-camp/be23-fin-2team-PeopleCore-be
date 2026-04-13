package com.peoplecore.resign.repository;

import com.peoplecore.resign.domain.Resign;
import com.peoplecore.resign.domain.RetireStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ResignRepository extends JpaRepository<Resign, Long>,ResignRepositoryCustom {

//    상태별 카운트 (ACTIVE / CONFIRMED / RESIGNED)
    long countByEmployee_Company_CompanyIdAndIsDeletedFalseAndRetireStatus(UUID companyId, RetireStatus retireStatus);


//    퇴직상세 조회
    @Query("""
            SELECT r FROM Resign r
            JOIN FETCH r.employee e
            JOIN FETCH r.department
            JOIN FETCH r.grade
            LEFT JOIN FETCH r.title
            WHERE e.company.companyId = :companyId
            AND r.resignId = :resignId
            AND r.isDeleted = false
            """)
    Optional<Resign> findDetailByCompanyAndId(@Param("companyId") UUID companyId,
                                              @Param("resignId") Long resignId);

//    퇴직 처리용 - 회사Id+resignId로 미삭제 건 조회
    Optional<Resign>findByResignIdAndEmployee_Company_CompanyIdAndIsDeletedFalse(Long resignId, UUID companyId);

//    스케줄러용 - CONFIRMED 상태이고 퇴직예정일 도래한 건 조회
    @Query("""
            SELECT r FROM Resign r
            JOIN FETCH r.employee
            WHERE r.retireStatus = :status
            AND r.isDeleted = false
            AND r.resignDate <= :date
            """)
    List<Resign> findAllByRetireStatusAndIsDeletedFalseAndResignDateLessThanEqual(
            @Param("status") RetireStatus status, @Param("date") LocalDate date);
}
