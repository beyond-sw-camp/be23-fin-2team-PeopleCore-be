package com.peoplecore.pay.transfer;

import com.peoplecore.pay.dtos.PayrollTransferDto;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class KbTransferGenerator implements BankTransferFileGenerator {

    @Override
    public String getBankCode() {
        return "004";
    }

    @Override
    public byte[] generate(List<PayrollTransferDto> transfer) {
        StringBuilder sb = new StringBuilder();
//        KB: 은행코드, 입금계좌번호, 이체금액 (제목행 없음)
        for (PayrollTransferDto t : transfer){
            sb.append(t.getBankCode()).append(",")
                    .append(t.getAccountNumber()).append(",")
                    .append(t.getNetPay()).append("\n");
        }
        // UTF-8 with BOM
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] content = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[bom.length + content.length];
        System.arraycopy(bom, 0, result, 0, bom.length);
        System.arraycopy(content, 0, result, bom.length, content.length);

        return result;
    }

    @Override
    public String getFileName(String payYearMonth) {
        return "급여이체_KB_" + payYearMonth + ".csv";
    }
}
