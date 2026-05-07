package com.peoplecore.auth.service;

import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Service
public class EmailAuthService {

    private final StringRedisTemplate emailRedis;
    private final EmailSender emailSender;
    private final EmployeeRepository employeeRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    private static final long CODE_TTL = 3;      // 인증코드 유효시간 3분
    private static final long VERIFIED_TTL = 10; // 인증 완료 상태 유지 10분
    private static final long COOLDOWN_SEC = 60; // 재발송 쿨다운 1분
    private static final int MAX_FAIL = 5;       // 최대 실패 횟수

    public EmailAuthService(
            @Qualifier("emailRedisTemplate") StringRedisTemplate emailRedis,
            EmailSender emailSender,
            EmployeeRepository employeeRepository) {
        this.emailRedis = emailRedis;
        this.emailSender = emailSender;
        this.employeeRepository = employeeRepository;
    }

    public void sendCode(String empPersonalEmail) {
        // 사원 존재 확인
        employeeRepository.findByEmpPersonalEmail(empPersonalEmail)
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

        // 쿨다운 확인
        String cooldownKey = "EMAIL_COOLDOWN:" + empPersonalEmail;
        if (Boolean.TRUE.equals(emailRedis.hasKey(cooldownKey))) {
            throw new CustomException(ErrorCode.EMAIL_COOLDOWN);
        }

        // 인증코드 생성 및 저장
        String code = String.format("%06d", secureRandom.nextInt(1_000_000));

        emailRedis.opsForValue().set("EMAIL_CODE:" + empPersonalEmail, code, CODE_TTL, TimeUnit.MINUTES);
        emailRedis.opsForValue().set(cooldownKey, "wait", COOLDOWN_SEC, TimeUnit.SECONDS);

        // 발송 실패 시 저장한 코드/쿨다운 롤백
        try {
            emailSender.send(empPersonalEmail, code);
        } catch (RuntimeException e) {
            emailRedis.delete("EMAIL_CODE:" + empPersonalEmail);
            emailRedis.delete(cooldownKey);
            throw e;
        }
    }

    public void verify(String empEmail, String inputCode) {
        String blockKey = "EMAIL_BLOCK:" + empEmail;
        String failKey = "EMAIL_FAIL:" + empEmail;

        if (Boolean.TRUE.equals(emailRedis.hasKey(blockKey))) {
            throw new CustomException(ErrorCode.EMAIL_BLOCKED);
        }

        String savedCode = emailRedis.opsForValue().get("EMAIL_CODE:" + empEmail);

        if (savedCode == null) {
            incrementFail(failKey, blockKey);
            throw new CustomException(ErrorCode.EMAIL_CODE_EXPIRED);
        }

        if (!savedCode.equals(inputCode)) {
            incrementFail(failKey, blockKey);
            throw new CustomException(ErrorCode.EMAIL_CODE_MISMATCH);
        }

        // 인증 성공 → 실패 카운트/코드 삭제, 인증 완료 표시
        emailRedis.delete(failKey);
        emailRedis.delete("EMAIL_CODE:" + empEmail);
        emailRedis.opsForValue().set("EMAIL_VERIFIED:" + empEmail, "true", VERIFIED_TTL, TimeUnit.MINUTES);
    }

    public void checkVerified(String empEmail) {
        String verified = emailRedis.opsForValue().get("EMAIL_VERIFIED:" + empEmail);
        if (!"true".equals(verified)) {
            throw new CustomException(ErrorCode.EMAIL_NOT_VERIFIED);
        }
    }

    public void clearVerified(String empEmail) {
        emailRedis.delete("EMAIL_VERIFIED:" + empEmail);
    }

    private void incrementFail(String failKey, String blockKey) {
        Long count = emailRedis.opsForValue().increment(failKey);

        if (count != null && count == 1) {
            emailRedis.expire(failKey, 10, TimeUnit.MINUTES);
        }

        if (count != null && count >= MAX_FAIL) {
            emailRedis.opsForValue().set(blockKey, "true", 10, TimeUnit.MINUTES);
            emailRedis.delete(failKey);
        }
    }
}
