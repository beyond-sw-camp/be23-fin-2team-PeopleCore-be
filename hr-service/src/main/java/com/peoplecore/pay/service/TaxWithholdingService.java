package com.peoplecore.pay.service;

import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.pay.domain.TaxWithholdingTable;
import com.peoplecore.pay.dtos.TaxWithholdingResDto;
import com.peoplecore.pay.dtos.TaxWithholdingRowResDto;
import com.peoplecore.pay.repository.TaxWithholdingRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
@Slf4j
public class TaxWithholdingService {
// 회사별 등록 X, 시스템 전역

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
    public Page<TaxWithholdingRowResDto> getTableByYear(Integer year, Pageable pageable){
        Page<TaxWithholdingTable> page = taxWithholdingRepository.findByTaxYearOrderBySalaryMinAsc(year, pageable);
        if (page.isEmpty()){
            throw new CustomException(ErrorCode.TAX_TABLE_NOT_FOUND);
        }

        return page.map(TaxWithholdingRowResDto::fromEntity);
    }

//    세액 조회(급여계산시 호출용 : 급여+부양가족수 -> 세액) /  (천원 변환 + dep 컬럼 매핑)
//    해당 구간 데이터가 없을 수도 있는 정상 상황 → 예외 대신 null 반환
    public TaxWithholdingResDto getTax(Integer taxYear, Long monthlySalary, Integer dependents){
        // 원 → 천원 변환 (1000 미만은 버림)
        int salaryInThousand = (int) (monthlySalary / 1000);

        return taxWithholdingRepository.findByTaxYearAndSalaryMinLessThanEqualAndSalaryMaxGreaterThan(taxYear, salaryInThousand, salaryInThousand)
                .map(t -> {
                    long incomeTax = t.getTaxByDependents(dependents);
                    long localIncomeTax = Math.round(incomeTax * 0.1);
                    return TaxWithholdingResDto.builder()
                            .taxYear(t.getTaxYear())
                            .salaryMin(t.getSalaryMin())
                            .salaryMax(t.getSalaryMax())
                            .dependents(dependents)
                            .incomeTax(incomeTax)
                            .localIncomeTax(localIncomeTax)
                            .build();
                })
                .orElse(null);
    }


    // 엑셀 업로드 (세액 갱신시, 시스템운영자가 api 로 등록)
    @Transactional
    public int uploadFromExcel(Integer year, MultipartFile file) throws IOException {
        int deleted = taxWithholdingRepository.deleteByTaxYear(year);
        log.info("[간이세액표] year={} 기존 {}행 삭제", year, deleted);

        List<TaxWithholdingTable> entities = new ArrayList<>();

        try (Workbook wb = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            final int HEADER_ROWS = 3;   // 본인 엑셀 양식에 맞춰 조정

            for (int rowIdx = HEADER_ROWS; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null) continue;
                Cell c0 = row.getCell(0);
                Cell c1 = row.getCell(1);
                if (c0 == null || c1 == null) continue;

                int salaryMin = (int) c0.getNumericCellValue();
                int salaryMax = (int) c1.getNumericCellValue();

                entities.add(TaxWithholdingTable.builder()
                        .taxYear(year)
                        .salaryMin(salaryMin)
                        .salaryMax(salaryMax)
                        .taxDep01(getInt(row, 2))
                        .taxDep02(getInt(row, 3))
                        .taxDep03(getInt(row, 4))
                        .taxDep04(getInt(row, 5))
                        .taxDep05(getInt(row, 6))
                        .taxDep06(getInt(row, 7))
                        .taxDep07(getInt(row, 8))
                        .taxDep08(getInt(row, 9))
                        .taxDep09(getInt(row, 10))
                        .taxDep10(getInt(row, 11))
                        .taxDep11(getInt(row, 12))
                        .build());
            }
        }

        taxWithholdingRepository.saveAll(entities);
        log.info("[간이세액표] year={} 신규 {}행 등록", year, entities.size());
        return entities.size();
    }

    private int getInt(Row row, int col) {
        Cell c = row.getCell(col);
        if (c == null || c.getCellType() == CellType.BLANK) return 0;
        return (int) c.getNumericCellValue();
    }
}
