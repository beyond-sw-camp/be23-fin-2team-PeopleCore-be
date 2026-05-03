package com.peoplecore.pay.transfer;

import com.peoplecore.pay.dtos.PayrollTransferDto;

import java.io.IOException;
import java.util.List;

public interface BankTransferFileGenerator {
//    대량이체 파일 생성
    String getBankCode();
    byte[] generate(List<PayrollTransferDto> transfer, String payYearMonth) throws IOException;
    String getFileName(String payYearMonth);
}
