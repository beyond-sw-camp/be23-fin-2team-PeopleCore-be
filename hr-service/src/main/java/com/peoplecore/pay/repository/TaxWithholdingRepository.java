package com.peoplecore.pay.repository;

import com.peoplecore.pay.domain.TaxWithholdingTable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TaxWithholdingRepository extends JpaRepository<TaxWithholdingTable, Long> {

    @Query("SELECT DISTINCT t.taxYear FROM TaxWithholdingTable t ORDER BY t.taxYear DESC")
    List<Integer> findDistinctTaxYears();

//     특정연도 세액표
    Page<TaxWithholdingTable> findByTaxYearOrderBySalaryFromAscDependentsAsc(Integer year, Pageable pageable);

//    세액조회 : 급여산정시 호출
     Optional<TaxWithholdingTable> findByTaxYearAndSalaryFromLessThanEqualAndSalaryToGreaterThanAndDependents(Integer taxYear, Long monthlyLessSalary, Long monthlyGreaterSalary, Integer dependents);

}

