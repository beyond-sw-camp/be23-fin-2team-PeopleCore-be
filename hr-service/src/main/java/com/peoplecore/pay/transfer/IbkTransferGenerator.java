package com.peoplecore.pay.transfer;

import com.peoplecore.pay.dtos.PayrollTransferDto;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class IbkTransferGenerator implements BankTransferFileGenerator {

    @Override
    public String getBankCode() {
        return "003";  // IBK기업은행
    }

    @Override
    public String getFileName(String payYearMonth) {
        return "급여이체_기업_" + payYearMonth + ".csv";
    }

    @Override
    public byte[] generate(List<PayrollTransferDto> transfers) {
        StringBuilder sb = new StringBuilder();
        // 기업은행: 입금은행코드, 계좌번호, 이체금액, 받는분성명, 메모
        for (PayrollTransferDto t : transfers) {
            sb.append(t.getBankCode()).append(",")
                    .append(t.getAccountNumber()).append(",")
                    .append(t.getNetPay()).append(",")
                    .append(t.getEmpName()).append(",")
                    .append(t.getMemo()).append("\n");
        }
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] content = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[bom.length + content.length];
        System.arraycopy(bom, 0, result, 0, bom.length);
        System.arraycopy(content, 0, result, bom.length, content.length);
        return result;
    }
}
