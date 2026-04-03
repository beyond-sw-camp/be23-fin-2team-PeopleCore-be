package com.peoplecore.sms;

import com.peoplecore.configs.CoolSmsConfig;
import lombok.extern.slf4j.Slf4j;
import net.nurigo.sdk.NurigoApp;
import net.nurigo.sdk.message.model.Message;
import net.nurigo.sdk.message.service.DefaultMessageService;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SmsSender {

    private final CoolSmsConfig config;

    public SmsSender(CoolSmsConfig config) {
        this.config = config;
    }

    public void send(String to, String code) {
        String cleanPhone = to.replaceAll("-", "");

        DefaultMessageService messageService =
                NurigoApp.INSTANCE.initialize(
                        config.getApiKey(),
                        config.getApiSecret(),
                        "https://api.solapi.com"
                );

        Message message = new Message();
        message.setFrom(config.getFromNumber());
        message.setTo(cleanPhone);
        message.setText("[PeopleCore] 인증번호: " + code);

        try {
            messageService.send(message);
            log.info("SMS 발송 성공 - 수신번호: {}", cleanPhone);
        } catch (Exception e) {
            log.error("SMS 발송 실패: {}", e.getMessage());
            throw new RuntimeException("SMS 발송에 실패했습니다.", e);
        }
    }
}
