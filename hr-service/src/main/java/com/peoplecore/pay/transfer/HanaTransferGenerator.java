package com.peoplecore.pay.transfer;

import com.peoplecore.pay.dtos.PayrollTransferDto;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class HanaTransferGenerator implements BankTransferFileGenerator {

    @Override
    public String getBankCode() {
        return "081";  // 하나은행
    }

    @Override
    public String getFileName(String payYearMonth) {
        return "급여이체_하나_" + payYearMonth + ".csv";
    }

    @Override
    public byte[] generate(List<PayrollTransferDto> transfers) {
        StringBuilder sb = new StringBuilder();
        // 하나은행: 입금은행코드, 계좌번호, 예금주명, 이체금액, 적요
        for (PayrollTransferDto t : transfers) {
            sb.append(t.getBankCode()).append(",")
                    .append(t.getAccountNumber()).append(",")
                    .append(t.getEmpName()).append(",")
                    .append(t.getNetPay()).append(",")
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
