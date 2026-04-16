package com.peoplecore.pay.transfer;

import com.peoplecore.pay.dtos.PayrollTransferDto;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class ShinhanTransferGenerator implements BankTransferFileGenerator {

    @Override
    public String getBankCode() { return "088"; }

    @Override
    public String getFileName(String payYearMonth) {
        return "급여이체_신한_" + payYearMonth + ".csv";
    }

    @Override
    public byte[] generate(List<PayrollTransferDto> transfers) {
        StringBuilder sb = new StringBuilder();
        // 신한: 입금은행, 입금계좌, 고객관리성명, 입금액 (제목행 없음)
        for (PayrollTransferDto t : transfers) {
            sb.append(t.getBankCode()).append(",")
                    .append(t.getAccountNumber()).append(",")
                    .append(t.getEmpName()).append(",")
                    .append(t.getNetPay()).append("\n");
        }
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] content = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[bom.length + content.length];
        System.arraycopy(bom, 0, result, 0, bom.length);
        System.arraycopy(content, 0, result, bom.length, content.length);
        return result;
    }
}