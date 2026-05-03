package com.peoplecore.pay.transfer;

import com.peoplecore.pay.dtos.PayrollTransferDto;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
public class WooriTransferGenerator implements BankTransferFileGenerator {

    private final ExcelTransferBuilder builder = new ExcelTransferBuilder();

    @Override
    public String getBankCode() {
        return "020";
    }

    @Override
    public String getFileName(String payYearMonth) {
        return "급여이체_우리_" + payYearMonth + ".xlsx";
    }

    @Override
    public byte[] generate(List<PayrollTransferDto> transfer, String payYearMonth) throws IOException {
        return builder.build(transfer, "우리은행", payYearMonth, List.of(
                new ExcelTransferBuilder.Column.Text("입금은행코드", PayrollTransferDto::getBankCode),
                new ExcelTransferBuilder.Column.Text("입금계좌번호", PayrollTransferDto::getAccountNumber),
                new ExcelTransferBuilder.Column.Text("받는분 성명",  PayrollTransferDto::getEmpName),
                new ExcelTransferBuilder.Column.Numeric("이체금액",  PayrollTransferDto::getNetPay),
                new ExcelTransferBuilder.Column.Text("비고",         PayrollTransferDto::getMemo)
        ));
    }
}
