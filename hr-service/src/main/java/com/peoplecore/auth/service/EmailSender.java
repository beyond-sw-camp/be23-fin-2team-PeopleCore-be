package com.peoplecore.auth.service;

import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmailSender {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String from;

    public EmailSender(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void send(String to, String code) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(to);
        msg.setFrom(from);
        msg.setSubject("[PeopleCore] 비밀번호 재설정 인증코드");
        msg.setText("인증코드: " + code + "\n\n인증코드는 3분간 유효합니다.");

        try {
            mailSender.send(msg);
            log.info("이메일 발송 성공 - 수신: {}", to);
        } catch (Exception e) {
            log.error("이메일 발송 실패 - 수신: {}, 사유: {}", to, e.getMessage());
            throw new CustomException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }
}
