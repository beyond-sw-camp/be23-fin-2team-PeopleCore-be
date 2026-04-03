package com.peoplecore.auth.service;

import com.peoplecore.common.exception.CustomException;
import com.peoplecore.common.exception.ErrorCode;
import com.peoplecore.employee.repository.EmployeeRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;


import com.peoplecore.common.sms.SmsSender;


@Service
public class SmsAuthService {

    private final StringRedisTemplate smsRedis;
    private final SmsSender smsSender;
    private final EmployeeRepository employeeRepository;

    private static final long CODE_TTL = 3;
    private static final int MAX_FAIL = 5;

    public SmsAuthService(
            @Qualifier("smsRedisTemplate") StringRedisTemplate smsRedis,
            SmsSender smsSender,
            EmployeeRepository employeeRepository) {
        this.smsRedis = smsRedis;
        this.smsSender = smsSender;
        this.employeeRepository = employeeRepository;
    }

    public void sendCode(UUID companyId, String empName, String empPhone) {
        employeeRepository.findByCompanyIdAndEmpNameAndEmpPhone(companyId, empName, empPhone)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));

        String cooldownKey = "SMS_COOLDOWN:" + empPhone;
        if (Boolean.TRUE.equals(smsRedis.hasKey(cooldownKey))) {
            throw new CustomException(ErrorCode.SMS_COOLDOWN);
        }

        String code = String.valueOf((int) (Math.random() * 900000) + 100000);

        smsRedis.opsForValue().set("SMS_CODE:" + empPhone, code, CODE_TTL, TimeUnit.MINUTES);
        smsRedis.opsForValue().set(cooldownKey, "wait", 60, TimeUnit.SECONDS);

        smsSender.send(empPhone, code);
    }

    public void verify(UUID companyId, String empName, String empPhone, String inputCode) {
        String blockKey = "SMS_BLOCK:" + empPhone;
        String failKey = "SMS_FAIL:" + empPhone;

        if (Boolean.TRUE.equals(smsRedis.hasKey(blockKey))) {
            throw new CustomException(ErrorCode.SMS_BLOCKED);
        }

        String savedCode = smsRedis.opsForValue().get("SMS_CODE:" + empPhone);

        if (savedCode == null) {
            incrementFail(empPhone, failKey, blockKey);
            throw new CustomException(ErrorCode.SMS_CODE_EXPIRED);
        }

        if (!savedCode.equals(inputCode)) {
            incrementFail(empPhone, failKey, blockKey);
            throw new CustomException(ErrorCode.SMS_CODE_MISMATCH);
        }

        smsRedis.delete(failKey);
        smsRedis.delete("SMS_CODE:" + empPhone);
        smsRedis.opsForValue().set("SMS_VERIFIED:" + empPhone, "true", 10, TimeUnit.MINUTES);
    }

    public void checkVerified(String empPhone) {
        String verified = smsRedis.opsForValue().get("SMS_VERIFIED:" + empPhone);
        if (!"true".equals(verified)) {
            throw new CustomException(ErrorCode.SMS_NOT_VERIFIED);
        }
    }

    public void clearVerified(String empPhone) {
        smsRedis.delete("SMS_VERIFIED:" + empPhone);
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
