package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.TaxWithholdingTable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TaxWithholdingRepository extends JpaRepository<TaxWithholdingTable, Long> {

    @Query("SELECT DISTINCT t.taxYear FROM TaxWithholdingTable t ORDER BY t.taxYear DESC")
    List<Integer> findDistinctTaxYears();


//     특정연도 세액표
    Page<TaxWithholdingTable> findByTaxYearOrderBySalaryMinAsc(Integer year, Pageable pageable);

//    세액조회 : 급여산정시 호출 (salary는 천원 단위로 비교)
     Optional<TaxWithholdingTable> findByTaxYearAndSalaryMinLessThanEqualAndSalaryMaxGreaterThan(
             Integer taxYear, Integer salaryMin, Integer salaryMax);

//    해당 연도 표의 마지막(최대 salaryMax) 행 - 세액 최대치 구간
    Optional<TaxWithholdingTable> findTopByTaxYearOrderBySalaryMaxDesc(Integer taxYear);

    // 업로드 시 같은 연도 데이터 갱신용
    @Modifying
    @Query("DELETE FROM TaxWithholdingTable t WHERE t.taxYear = :year")
    int deleteByTaxYear(@Param("year") Integer year);

}

