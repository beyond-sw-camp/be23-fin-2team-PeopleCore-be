package com.peoplecore.pay.service;

import com.peoplecore.company.domain.Company;
import com.peoplecore.company.repository.CompanyRepository;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.pay.domain.PayItems;
import com.peoplecore.pay.dtos.PayItemReqDto;
import com.peoplecore.pay.dtos.PayItemResDto;
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
}
