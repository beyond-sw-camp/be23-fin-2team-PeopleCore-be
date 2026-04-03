package com.peoplecore.auth.service;

import com.peoplecore.common.exception.CustomException;
import com.peoplecore.common.exception.ErrorCode;
import com.peoplecore.auth.dto.PasswordResetRequest;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
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
        smsAuthService.checkVerified(request.getEmpPhone());

        Employee employee = employeeRepository.findByEmpPhone(request.getEmpPhone())
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));

        if (passwordEncoder.matches(request.getNewPassword(), employee.getEmpPassword())) {
            throw new CustomException(ErrorCode.SAME_PASSWORD);
        }

        employee.changePassword(passwordEncoder.encode(request.getNewPassword()));

        smsAuthService.clearVerified(request.getEmpPhone());
    }
}
