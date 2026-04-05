package com.peoplecore.pay.service;

import com.peoplecore.company.domain.Company;
import com.peoplecore.company.repository.CompanyRepository;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.pay.domain.PayItems;
import com.peoplecore.pay.dtos.PayItemReqDto;
import com.peoplecore.pay.dtos.PayItemResDto;
import com.peoplecore.pay.enums.LegalCalcType;
import com.peoplecore.pay.enums.PayItemCategory;
import com.peoplecore.pay.enums.PayItemType;
import com.peoplecore.pay.repository.PayItemSearchRepository;
import com.peoplecore.pay.repository.PayItemsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class PayItemsService {

    private final PayItemsRepository payItemsRepository;
    private final PayItemSearchRepository payItemSearchRepository;
    private final CompanyRepository companyRepository;

    @Autowired
    public PayItemsService(PayItemsRepository payItemsRepository, PayItemSearchRepository payItemSearchRepository, CompanyRepository companyRepository) {
        this.payItemsRepository = payItemsRepository;
        this.payItemSearchRepository = payItemSearchRepository;
        this.companyRepository = companyRepository;
    }

//    목록조회 : 지급 or 공제, 항목명검색
    public List<PayItemResDto> getPayItems(UUID companyId, PayItemType type, String name){
        return payItemSearchRepository.search(companyId, type, name)
                .stream()
                .map(PayItemResDto::fromEntity)
                .toList();
    }

    @Transactional
    public PayItemResDto createPayItem(UUID companyId, PayItemReqDto reqDto){
        Company company = companyRepository.findById(companyId).orElseThrow(()-> new CustomException(ErrorCode.NOT_FOUND));

        PayItems items = PayItems.builder()
                .payItemName(reqDto.getPayItemName())
                .payItemType(reqDto.getPayItemType())
                .isFixed(reqDto.getIsFixed())
                .isTaxable(reqDto.getIsTaxable())
                .taxExemptLimit(reqDto.getTaxExemptLimit())
                .payItemCategory(reqDto.getPayItemCategory())
                .sortOrder(reqDto.getSortOrder())
                .isActive(true)
                .isLegal(false)
                .company(company)
                .build();

        return PayItemResDto.fromEntity(payItemsRepository.save(items));
    }

    @Transactional
    public PayItemResDto updatePayItem(UUID companyId, Long payItemId, PayItemReqDto reqDto){

        PayItems items = payItemsRepository.findByPayItemIdAndCompany_CompanyId(payItemId, companyId).orElseThrow(()-> new CustomException(ErrorCode.NOT_FOUND));

        items.update(reqDto.getPayItemName(), reqDto.getIsFixed(), reqDto.getIsTaxable(), reqDto.getTaxExemptLimit(), reqDto.getPayItemCategory());

        return PayItemResDto.fromEntity(items);
    }

    @Transactional
    public PayItemResDto toggleStatus(UUID companyId, Long payItemId){
        PayItems items = payItemsRepository.findByPayItemIdAndCompany_CompanyId(payItemId, companyId).orElseThrow(()-> new CustomException(ErrorCode.NOT_FOUND));

        items.toggleActive();
        return PayItemResDto.fromEntity(items);

    }

    @Transactional
    public void deletePayItems(UUID companyId, List<Long> payItemIds){
        List<PayItems> items = payItemsRepository.findByPayItemIdInAndCompany_CompanyId(payItemIds ,companyId);

        if (items.size() != payItemIds.size()) {
            throw new CustomException(ErrorCode.NOT_FOUND);
        }

        payItemsRepository.deleteAll(items);
    }


    // 회사 생성시 기본 세팅값 저장
    @Transactional
    public void initDefault(Company company) {
        List<PayItems> defaults = List.of(

                // ══════ 지급항목 (PAYMENT) ══════

                // 기본급 — 과세, 고정
                PayItems.builder()
                        .company(company).payItemName("기본급")
                        .payItemType(PayItemType.PAYMENT)
                        .payItemCategory(PayItemCategory.SALARY)
                        .isTaxable(true).isFixed(false)
                        .isActive(true).sortOrder(1)
                        .taxExemptLimit(0)
                        .build(),
                // 직책수당 — 고정, 과세
                PayItems.builder()
                        .company(company).payItemName("직책수당")
                        .payItemType(PayItemType.PAYMENT)
                        .payItemCategory(PayItemCategory.ALLOWANCE)
                        .isTaxable(true).isFixed(true)
                        .isActive(true).sortOrder(2)
                        .taxExemptLimit(0)
                        .build(),
                // 식대 — 고정, 비과세, 한도 200,000
                PayItems.builder()
                        .company(company).payItemName("식대")
                        .payItemType(PayItemType.PAYMENT)
                        .payItemCategory(PayItemCategory.ALLOWANCE)
                        .isTaxable(false).isFixed(true)
                        .isActive(true).sortOrder(3)
                        .taxExemptLimit(200000)
                        .build(),
                // 교통비 — 고정, 비과세, 한도 200,000
                PayItems.builder()
                        .company(company).payItemName("교통비")
                        .payItemType(PayItemType.PAYMENT)
                        .payItemCategory(PayItemCategory.ALLOWANCE)
                        .isTaxable(false).isFixed(true)
                        .isActive(true).sortOrder(4)
                        .taxExemptLimit(200000)
                        .build(),
                // 연장근로수당 — 법정수당
                PayItems.builder()
                        .company(company).payItemName("연장근로수당")
                        .payItemType(PayItemType.PAYMENT)
                        .payItemCategory(PayItemCategory.ALLOWANCE)
                        .isTaxable(true).isFixed(false)
                        .isActive(true).sortOrder(5)
                        .isLegal(true).legalCalcType(LegalCalcType.OVERTIME)
                        .taxExemptLimit(0)
                        .build(),
                // 야간근로수당 — 법정수당
                PayItems.builder()
                        .company(company).payItemName("야간근로수당")
                        .payItemType(PayItemType.PAYMENT)
                        .payItemCategory(PayItemCategory.ALLOWANCE)
                        .isTaxable(true).isFixed(false)
                        .isActive(true).sortOrder(6)
                        .isLegal(true).legalCalcType(LegalCalcType.NIGHT)
                        .taxExemptLimit(0)
                        .build(),
                // 휴일근로수당 — 법정수당
                PayItems.builder()
                        .company(company).payItemName("휴일근로수당")
                        .payItemType(PayItemType.PAYMENT)
                        .payItemCategory(PayItemCategory.ALLOWANCE)
                        .isTaxable(true).isFixed(false)
                        .isActive(true).sortOrder(7)
                        .isLegal(true).legalCalcType(LegalCalcType.HOLIDAY)
                        .taxExemptLimit(0)
                        .build(),
                // 연차수당 — 법정수당
                PayItems.builder()
                        .company(company).payItemName("연차수당")
                        .payItemType(PayItemType.PAYMENT)
                        .payItemCategory(PayItemCategory.ALLOWANCE)
                        .isTaxable(true).isFixed(false)
                        .isActive(true).sortOrder(8)
                        .isLegal(true).legalCalcType(LegalCalcType.LEAVE)
                        .taxExemptLimit(0)
                        .build(),
                // 상여금
                PayItems.builder()
                        .company(company).payItemName("상여금")
                        .payItemType(PayItemType.PAYMENT)
                        .payItemCategory(PayItemCategory.BONUS)
                        .isTaxable(true).isFixed(false)
                        .isActive(true).sortOrder(9)
                        .taxExemptLimit(0)
                        .build(),
                // 교육비지원금 — 고정, 비활성
                PayItems.builder()
                        .company(company).payItemName("교육비지원금")
                        .payItemType(PayItemType.PAYMENT)
                        .payItemCategory(PayItemCategory.ALLOWANCE)
                        .isTaxable(true).isFixed(true)
                        .isActive(false).sortOrder(10)
                        .taxExemptLimit(0)
                        .build(),
                // 결근차감 — 비활성
                PayItems.builder()
                        .company(company).payItemName("결근차감")
                        .payItemType(PayItemType.PAYMENT)
                        .payItemCategory(PayItemCategory.SALARY)
                        .isTaxable(true).isFixed(false)
                        .isActive(false).sortOrder(11)
                        .taxExemptLimit(0)
                        .build(),
                // 명절휴가수당 — 비활성
                PayItems.builder()
                        .company(company).payItemName("명절휴가수당")
                        .payItemType(PayItemType.PAYMENT)
                        .payItemCategory(PayItemCategory.BONUS)
                        .isTaxable(true).isFixed(false)
                        .isActive(false).sortOrder(12)
                        .taxExemptLimit(0)
                        .build(),

                // ══════ 공제항목 (DEDUCTION) ══════

                // 근로소득세
                PayItems.builder()
                        .company(company).payItemName("근로소득세")
                        .payItemType(PayItemType.DEDUCTION)
                        .payItemCategory(PayItemCategory.TAX)
                        .isTaxable(false).isFixed(false)
                        .isActive(true).sortOrder(1)
                        .taxExemptLimit(0)
                        .build(),
                // 근로지방소득세
                PayItems.builder()
                        .company(company).payItemName("근로지방소득세")
                        .payItemType(PayItemType.DEDUCTION)
                        .payItemCategory(PayItemCategory.TAX)
                        .isTaxable(false).isFixed(false)
                        .isActive(true).sortOrder(2)
                        .taxExemptLimit(0)
                        .build(),
                // 국민연금
                PayItems.builder()
                        .company(company).payItemName("국민연금")
                        .payItemType(PayItemType.DEDUCTION)
                        .payItemCategory(PayItemCategory.INSURANCE)
                        .isTaxable(false).isFixed(false)
                        .isActive(true).sortOrder(3)
                        .taxExemptLimit(0)
                        .build(),
                // 건강보험
                PayItems.builder()
                        .company(company).payItemName("건강보험")
                        .payItemType(PayItemType.DEDUCTION)
                        .payItemCategory(PayItemCategory.INSURANCE)
                        .isTaxable(false).isFixed(false)
                        .isActive(true).sortOrder(4)
                        .taxExemptLimit(0)
                        .build(),
                // 장기요양보험
                PayItems.builder()
                        .company(company).payItemName("장기요양보험")
                        .payItemType(PayItemType.DEDUCTION)
                        .payItemCategory(PayItemCategory.INSURANCE)
                        .isTaxable(false).isFixed(false)
                        .isActive(true).sortOrder(5)
                        .taxExemptLimit(0)
                        .build(),
                // 고용보험
                PayItems.builder()
                        .company(company).payItemName("고용보험")
                        .payItemType(PayItemType.DEDUCTION)
                        .payItemCategory(PayItemCategory.INSURANCE)
                        .isTaxable(false).isFixed(false)
                        .isActive(true).sortOrder(6)
                        .taxExemptLimit(0)
                        .build(),
                // 학자금상환
                PayItems.builder()
                        .company(company).payItemName("학자금상환")
                        .payItemType(PayItemType.DEDUCTION)
                        .payItemCategory(PayItemCategory.OTHER_DEDUCTION)
                        .isTaxable(false).isFixed(false)
                        .isActive(true).sortOrder(7)
                        .taxExemptLimit(0)
                        .build()
        );
        payItemsRepository.saveAll(defaults);
    }
}
