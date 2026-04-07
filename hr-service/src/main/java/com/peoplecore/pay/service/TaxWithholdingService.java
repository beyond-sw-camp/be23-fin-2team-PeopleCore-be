package com.peoplecore.pay.service;

import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.pay.domain.TaxWithholdingTable;
import com.peoplecore.pay.dtos.TaxWithholdingResDto;
import com.peoplecore.pay.repository.TaxWithholdingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class TaxWithholdingService {

    private final TaxWithholdingRepository taxWithholdingRepository;

    @Autowired
    public TaxWithholdingService(TaxWithholdingRepository taxWithholdingRepository) {
        this.taxWithholdingRepository = taxWithholdingRepository;
    }

//    등록된 연도 목록
    public List<Integer> getYearList(){
         return taxWithholdingRepository.findDistinctTaxYears();
    }

//    특정연도 세액표 조회
    public Page<TaxWithholdingResDto> getTableByYear(Integer year, Pageable pageable){
        Page<TaxWithholdingTable> page = taxWithholdingRepository.findByTaxYearOrderBySalaryFromAscDependentsAsc(year, pageable);
        if (page.isEmpty()){
            throw new CustomException(ErrorCode.TAX_TABLE_NOT_FOUND);
        }

        return page.map(TaxWithholdingResDto::fromEntity);
    }

//    세액 조회(급여계산시 호출용 : 급여+부양가족수 -> 세액)
    public TaxWithholdingResDto getTax(Integer taxYear, Long monthlySalary, Integer dependents){
        TaxWithholdingTable taxTable = taxWithholdingRepository.findByTaxYearAndSalaryFromLessThanEqualAndSalaryToGreaterThanAndDependents(taxYear, monthlySalary, monthlySalary,dependents)
                .orElseThrow(()-> new CustomException(ErrorCode.TAX_TABLE_LOOKUP_FAILED));

        return TaxWithholdingResDto.fromEntity(taxTable);
    }

}
