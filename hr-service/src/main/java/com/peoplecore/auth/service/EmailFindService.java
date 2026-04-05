package com.peoplecore.auth.service;

import com.peoplecore.auth.dto.EmailFindResponse;
import com.peoplecore.auth.dto.SmsVerifyRequest;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class
EmailFindService {

    private final SmsAuthService smsAuthService;
    private final EmployeeRepository employeeRepository;

    @Transactional(readOnly = true)
    public EmailFindResponse findEmail(SmsVerifyRequest request) {
        // 1. SMS 인증 수행 (실패 시 CustomException 발생)
        smsAuthService.verify(request.getCompanyId(), request.getEmpName(), request.getEmpPhone(), request.getCode());

        // 2. 사원 조회
        Employee employee = employeeRepository.findByCompany_CompanyIdAndEmpNameAndEmpPhone(
                request.getCompanyId(), request.getEmpName(), request.getEmpPhone())
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));

        // 3. 이메일 마스킹 및 반환
        String maskedEmail = maskEmail(employee.getEmpEmail());

        // 4. 인증 상태 제거 (이메일 찾기 완료 후 재사용 방지)
        smsAuthService.clearVerified(request.getEmpPhone());

        return new EmailFindResponse(maskedEmail);
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }

        String[] parts = email.split("@");
        String id = parts[0];
        String domain = parts[1];

        if (id.length() <= 3) {
            // "abc@domain.com" -> "a**@domain.com"
            return id.charAt(0) + "**@" + domain;
        }

        // "john@domain.com" -> "jo***@domain.com"
        return id.substring(0, 2) + "***@" + domain;
    }
}
