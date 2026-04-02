package com.peoplecore.hr_service.auth.service;

import com.peoplecore.common.exception.CustomException;
import com.peoplecore.common.exception.ErrorCode;
import com.peoplecore.hr_service.auth.dto.PasswordResetRequest;
import com.peoplecore.hr_service.employee.domain.Employee;
import com.peoplecore.hr_service.employee.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final SmsAuthService smsAuthService;

    @Transactional
    public void resetPassword(PasswordResetRequest request) {
        // 1. SMS 인증 완료 여부 확인
        smsAuthService.checkVerified(request.getEmpPhone());

        // 2. 사원 조회 (전화번호로)
        Employee employee = employeeRepository.findByEmpPhone(request.getEmpPhone())
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));

        // 3. 기존 비밀번호와 동일한지 확인
        if (passwordEncoder.matches(request.getNewPassword(), employee.getEmpPassword())) {
            throw new CustomException(ErrorCode.SAME_PASSWORD);
        }

        // 4. 비밀번호 변경
        employee.changePassword(passwordEncoder.encode(request.getNewPassword()));

        // 5. 인증 상태 제거 (재사용 방지)
        smsAuthService.clearVerified(request.getEmpPhone());
    }
}