package com.peoplecore.pay.transfer;

import com.peoplecore.pay.dtos.PayrollTransferDto;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
public class KbTransferGenerator implements BankTransferFileGenerator {

    private final ExcelTransferBuilder builder = new ExcelTransferBuilder();

    @Override
    public String getBankCode() {
        return "004";
    }

    @Override
    public String getFileName(String payYearMonth) {
        return "급여이체_KB국민_" + payYearMonth + ".xlsx";
    }

    @Override
    public byte[] generate(List<PayrollTransferDto> transfer, String payYearMonth) throws IOException {
        return builder.build(transfer, "KB국민은행", payYearMonth, List.of(
                new ExcelTransferBuilder.Column.Text("입금은행코드", PayrollTransferDto::getBankCode),
                new ExcelTransferBuilder.Column.Text("입금계좌번호", PayrollTransferDto::getAccountNumber),
                new ExcelTransferBuilder.Column.Text("예금주",       PayrollTransferDto::getEmpName),
                new ExcelTransferBuilder.Column.Numeric("이체금액",  PayrollTransferDto::getNetPay),
                new ExcelTransferBuilder.Column.Text("CMS코드",     t -> ""),
                new ExcelTransferBuilder.Column.Text("적요",         PayrollTransferDto::getMemo)
        ));
    }
}
