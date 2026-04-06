package com.peoplecore.pay.service;

import com.peoplecore.company.domain.Company;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.pay.domain.CompanyPaySettings;
import com.peoplecore.pay.dtos.BankResDto;
import com.peoplecore.pay.dtos.PaySettingsReqDto;
import com.peoplecore.pay.dtos.PaySettingsResDto;
import com.peoplecore.pay.enums.PayMonth;
import com.peoplecore.pay.repository.PaySettingsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional(readOnly = true)
public class PaySettingsService {

    private final PaySettingsRepository paySettingsRepository;

    // 유효 은행코드 목록 (금융결제원 표준, 순서 보장 LinkedHashMap)
    private static final Map<String, String> BANK_MAP;
    static {
        BANK_MAP = new LinkedHashMap<>();
        BANK_MAP.put("003", "기업은행");
        BANK_MAP.put("004", "국민은행");
        BANK_MAP.put("011", "NH농협은행");
        BANK_MAP.put("020", "우리은행");
        BANK_MAP.put("023", "SC제일은행");
        BANK_MAP.put("027", "한국씨티은행");
        BANK_MAP.put("032", "부산은행");
        BANK_MAP.put("039", "경남은행");
        BANK_MAP.put("081", "하나은행");
        BANK_MAP.put("088", "신한은행");
        BANK_MAP.put("090", "카카오뱅크");
        BANK_MAP.put("092", "토스뱅크");
    }

    @Autowired
    public PaySettingsService(PaySettingsRepository paySettingsRepository) {
        this.paySettingsRepository = paySettingsRepository;
    }


    public List<BankResDto> getBankList(){
        List<BankResDto> result = new ArrayList<>();
        for(Map.Entry<String, String> entry : BANK_MAP.entrySet()){
            result.add(new BankResDto(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    public PaySettingsResDto getPaySettings(UUID companyId){
        return PaySettingsResDto.fromEntity(findSettings(companyId));
    }

    @Transactional
    public PaySettingsResDto updatePaySettings(UUID companyId, PaySettingsReqDto reqDto){

        if (reqDto.getSalaryPayLastDay() == Boolean.TRUE && reqDto.getSalaryPayDay() != null){
            throw new CustomException(ErrorCode.PAY_LAST_DAY_CONFLICT);
        }
        if(reqDto.getSalaryPayLastDay() == Boolean.FALSE && reqDto.getSalaryPayDay() == null){
            throw new CustomException(ErrorCode.PAY_INVALID_PAYMENT_DAY);
        }

        String bankName = Optional.ofNullable(BANK_MAP.get(reqDto.getMainBankCode())).orElseThrow(()-> new CustomException(ErrorCode.PAY_INVALID_BANK_CODE));

        CompanyPaySettings settings = findSettings(companyId);
        settings.update(
                reqDto.getSalaryPayLastDay() == Boolean.TRUE ? null : reqDto.getSalaryPayDay(),
                reqDto.getSalaryPayLastDay(),
                reqDto.getSalaryPayMonth(), reqDto.getMainBankCode(), bankName);

        return PaySettingsResDto.fromEntity(settings);
    }


//    회사 생성시 주거래은행 기본 세팅
    @Transactional
    public void initDefault(Company company) {
        CompanyPaySettings settings = CompanyPaySettings.builder()
                .company(company)
                .salaryPayMonth(PayMonth.NEXT)
                .salaryPayDay(25)
                .salaryPayLastDay(false)
                .mainBankCode("004")
                .mainBankName("국민은행")
                .build();
        paySettingsRepository.save(settings);
    }


    private CompanyPaySettings findSettings(UUID companyId){
        return paySettingsRepository.findByCompany_CompanyId(companyId).orElseThrow(()-> new CustomException(ErrorCode.PAY_SETTINGS_NOT_FOUND));
    }
}
