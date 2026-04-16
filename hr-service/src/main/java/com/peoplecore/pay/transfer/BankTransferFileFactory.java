package com.peoplecore.pay.transfer;

import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class BankTransferFileFactory {
//    은행코드로 구현체(대량이체) 찾기

    private final Map<String, BankTransferFileGenerator> generators;

    @Autowired
    public BankTransferFileFactory(List<BankTransferFileGenerator> generatorList) {
        this.generators = generatorList.stream()
                .collect(Collectors.toMap(BankTransferFileGenerator::getBankCode, g-> g));
    }

    public BankTransferFileGenerator getGenerator(String bankCode){
        BankTransferFileGenerator generator = generators.get(bankCode);
        if (generator == null){
            throw new CustomException(ErrorCode.UNSUPPORTED_BANK);
        }
        return generator;
    }
}
