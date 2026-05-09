package com.peoplecore.auth.service;

import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.pay.service.MySalaryCacheService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.concurrent.TimeUnit;

@Service
public class PersonalEmailService {

    private static final long CODE_TTL = 3;       // 인증코드 유효시간 3분
    private static final long COOLDOWN_SEC = 60;  // 재발송 쿨다운 1분
    private static final long BLOCK_TTL = 10;     // 차단 시간 10분
    private static final int MAX_FAIL = 5;        // 최대 실패 횟수

    // RFC 5322 간소화 패턴 — 일반적인 메일 주소 허용
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final StringRedisTemplate emailRedis;
    private final EmailSender emailSender;
    private final EmployeeRepository employeeRepository;
    private final MySalaryCacheService mySalaryCacheService;
    private final SecureRandom secureRandom = new SecureRandom();

    public PersonalEmailService(
            @Qualifier("emailRedisTemplate") StringRedisTemplate emailRedis,
            EmailSender emailSender,
            EmployeeRepository employeeRepository,
            MySalaryCacheService mySalaryCacheService) {
        this.emailRedis = emailRedis;
        this.emailSender = emailSender;
        this.employeeRepository = employeeRepository;
        this.mySalaryCacheService = mySalaryCacheService;
    }

    public void sendChangeCode(Long empId, String rawNewEmail) {
        String newEmail = normalize(rawNewEmail);
        validateFormat(newEmail);

        Employee me = employeeRepository.findById(empId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

        if (newEmail.equalsIgnoreCase(me.getEmpPersonalEmail())) {
            throw new CustomException(ErrorCode.PERSONAL_EMAIL_SAME_AS_CURRENT);
        }

        Optional<Employee> owner = employeeRepository.findByEmpPersonalEmail(newEmail);
        if (owner.isPresent() && !owner.get().getEmpId().equals(empId)) {
            throw new CustomException(ErrorCode.PERSONAL_EMAIL_DUPLICATE);
        }

        String cooldownKey = cooldownKey(empId);
        if (Boolean.TRUE.equals(emailRedis.hasKey(cooldownKey))) {
            throw new CustomException(ErrorCode.EMAIL_COOLDOWN);
        }

        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        String codeKey = codeKey(empId, newEmail);

        emailRedis.opsForValue().set(codeKey, code, CODE_TTL, TimeUnit.MINUTES);
        emailRedis.opsForValue().set(cooldownKey, "wait", COOLDOWN_SEC, TimeUnit.SECONDS);

        try {
            emailSender.sendPersonalEmailChangeCode(newEmail, code);
        } catch (RuntimeException e) {
            emailRedis.delete(codeKey);
            emailRedis.delete(cooldownKey);
            throw e;
        }
    }

    @Transactional
    public void verifyAndUpdate(Long empId, String rawNewEmail, String inputCode) {
        String newEmail = normalize(rawNewEmail);
        validateFormat(newEmail);

        String blockKey = blockKey(empId);
        String failKey = failKey(empId);
        String codeKey = codeKey(empId, newEmail);

        if (Boolean.TRUE.equals(emailRedis.hasKey(blockKey))) {
            throw new CustomException(ErrorCode.EMAIL_BLOCKED);
        }

        String savedCode = emailRedis.opsForValue().get(codeKey);
        if (savedCode == null) {
            incrementFail(failKey, blockKey);
            throw new CustomException(ErrorCode.EMAIL_CODE_EXPIRED);
        }
        if (!savedCode.equals(inputCode)) {
            incrementFail(failKey, blockKey);
            throw new CustomException(ErrorCode.EMAIL_CODE_MISMATCH);
        }

        Employee me = employeeRepository.findById(empId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));

        Optional<Employee> owner = employeeRepository.findByEmpPersonalEmail(newEmail);
        if (owner.isPresent() && !owner.get().getEmpId().equals(empId)) {
            throw new CustomException(ErrorCode.PERSONAL_EMAIL_DUPLICATE);
        }

        me.updatePersonalEmail(newEmail);

        // 내 급여 정보 캐시(empPersonalEmail 포함)를 무효화 — 즉시 새 값이 화면에 반영되도록
        mySalaryCacheService.evictSalaryInfoCache(me.getCompany().getCompanyId(), empId);

        emailRedis.delete(codeKey);
        emailRedis.delete(failKey);
        emailRedis.delete(cooldownKey(empId));
    }

    private void incrementFail(String failKey, String blockKey) {
        Long count = emailRedis.opsForValue().increment(failKey);
        if (count != null && count == 1) {
            emailRedis.expire(failKey, BLOCK_TTL, TimeUnit.MINUTES);
        }
        if (count != null && count >= MAX_FAIL) {
            emailRedis.opsForValue().set(blockKey, "true", BLOCK_TTL, TimeUnit.MINUTES);
            emailRedis.delete(failKey);
        }
    }

    private String normalize(String raw) {
        if (raw == null) throw new CustomException(ErrorCode.INVALID_EMAIL_FORMAT);
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) throw new CustomException(ErrorCode.INVALID_EMAIL_FORMAT);
        return trimmed.toLowerCase();
    }

    private void validateFormat(String email) {
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new CustomException(ErrorCode.INVALID_EMAIL_FORMAT);
        }
    }

    private String codeKey(Long empId, String newEmail) {
        return "PERSONAL_EMAIL_CODE:" + empId + ":" + newEmail;
    }

    private String cooldownKey(Long empId) {
        return "PERSONAL_EMAIL_COOLDOWN:" + empId;
    }

    private String failKey(Long empId) {
        return "PERSONAL_EMAIL_FAIL:" + empId;
    }

    private String blockKey(Long empId) {
        return "PERSONAL_EMAIL_BLOCK:" + empId;
    }
}
