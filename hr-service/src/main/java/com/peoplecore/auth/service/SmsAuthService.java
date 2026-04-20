package com.peoplecore.auth.service;

import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.employee.repository.EmployeeRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class SmsAuthService {

    private final StringRedisTemplate smsRedis;
    private final SmsSender smsSender;
    private final EmployeeRepository employeeRepository;

    private static final long CODE_TTL = 3;       // 인증코드 유효시간 3분
    private static final int MAX_FAIL = 5;         // 최대 실패 횟수

    public SmsAuthService(
            @Qualifier("smsRedisTemplate") StringRedisTemplate smsRedis,
            SmsSender smsSender,
            EmployeeRepository employeeRepository) {
        this.smsRedis = smsRedis;
        this.smsSender = smsSender;
        this.employeeRepository = employeeRepository;
    }

    public void sendCode(UUID companyId, String empName, String empBirthDate, String empPhone) {
        // 사원 존재 확인 (생년월일까지 일치해야 함, 전화번호는 하이픈 정규화)
        LocalDate birthDate = parseBirthDate(empBirthDate);
        String normalizedPhone = normalizePhone(empPhone);
        employeeRepository.findByCompanyAndNameAndBirthAndNormalizedPhone(companyId, empName, birthDate, normalizedPhone)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));

        sendCodeInternal(normalizedPhone);
    }

    public void sendCode(UUID companyId, String empName, String empPhone) {
        // 사원 존재 확인 (전화번호는 하이픈 정규화)
        String normalizedPhone = normalizePhone(empPhone);
        employeeRepository.findByCompanyAndNameAndNormalizedPhone(companyId, empName, normalizedPhone)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));

        sendCodeInternal(normalizedPhone);
    }

    private void sendCodeInternal(String empPhone) {

        // 1분 쿨다운 확인
        String cooldownKey = "SMS_COOLDOWN:" + empPhone;
        if (Boolean.TRUE.equals(smsRedis.hasKey(cooldownKey))) {
            throw new CustomException(ErrorCode.SMS_COOLDOWN);
        }

        // 인증코드 생성 및 저장
        String code = String.valueOf((int) (Math.random() * 900000) + 100000);

        smsRedis.opsForValue().set("SMS_CODE:" + empPhone, code, CODE_TTL, TimeUnit.MINUTES);
        smsRedis.opsForValue().set(cooldownKey, "wait", 60, TimeUnit.SECONDS);

        smsSender.send(empPhone, code);
    }

    public void verify(UUID companyId, String empName, String empPhone, String inputCode) {
        empPhone = normalizePhone(empPhone);
        String blockKey = "SMS_BLOCK:" + empPhone;
        String failKey = "SMS_FAIL:" + empPhone;

        // 차단 여부 확인
        if (Boolean.TRUE.equals(smsRedis.hasKey(blockKey))) {
            throw new CustomException(ErrorCode.SMS_BLOCKED);
        }

        // 저장된 코드 확인
        String savedCode = smsRedis.opsForValue().get("SMS_CODE:" + empPhone);

        if (savedCode == null) {
            incrementFail(empPhone, failKey, blockKey);
            throw new CustomException(ErrorCode.SMS_CODE_EXPIRED);
        }

        if (!savedCode.equals(inputCode)) {
            incrementFail(empPhone, failKey, blockKey);
            throw new CustomException(ErrorCode.SMS_CODE_MISMATCH);
        }

        // 인증 성공 → 실패 카운트 초기화, 인증 완료 표시
        smsRedis.delete(failKey);
        smsRedis.delete("SMS_CODE:" + empPhone);
        smsRedis.opsForValue().set("SMS_VERIFIED:" + empPhone, "true", 10, TimeUnit.MINUTES);
    }

    public void checkVerified(String empPhone) {
        empPhone = normalizePhone(empPhone);
        String verified = smsRedis.opsForValue().get("SMS_VERIFIED:" + empPhone);
        if (!"true".equals(verified)) {
            throw new CustomException(ErrorCode.SMS_NOT_VERIFIED);
        }
    }

    public void clearVerified(String empPhone) {
        empPhone = normalizePhone(empPhone);
        smsRedis.delete("SMS_VERIFIED:" + empPhone);
    }

    private String normalizePhone(String empPhone) {
        return empPhone == null ? null : empPhone.replaceAll("-", "");
    }

    private LocalDate parseBirthDate(String empBirthDate) {
        if (empBirthDate == null || empBirthDate.isBlank()) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }
        try {
            return LocalDate.parse(empBirthDate, DateTimeFormatter.ofPattern("yyyyMMdd"));
        } catch (DateTimeParseException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }
    }

    private void incrementFail(String empPhone, String failKey, String blockKey) {
        Long count = smsRedis.opsForValue().increment(failKey);

        if (count != null && count == 1) {
            smsRedis.expire(failKey, 10, TimeUnit.MINUTES);
        }

        if (count != null && count >= MAX_FAIL) {
            smsRedis.opsForValue().set(blockKey, "true", 10, TimeUnit.MINUTES);
            smsRedis.delete(failKey);
        }
    }
}