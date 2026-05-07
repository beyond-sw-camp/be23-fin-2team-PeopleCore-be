package com.peoplecore.auth.service;

import com.peoplecore.auth.dto.SimplePasswordStatusResponse;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SimplePasswordService {

    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public SimplePasswordService(EmployeeRepository employeeRepository, PasswordEncoder passwordEncoder) {
        this.employeeRepository = employeeRepository;
        this.passwordEncoder = passwordEncoder;
    }


    @Transactional(readOnly = true)
    public SimplePasswordStatusResponse status(Long empId) {
        Employee emp = employeeRepository.findById(empId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));
        return SimplePasswordStatusResponse.builder()
                .hasPin(emp.getSimplePassword() != null)
                .updatedAt(null)  // updatedAt 컬럼이 없음 — 필요하면 Employee에 추가
                .build();
    }

    @Transactional
    public void set(Long empId, String loginPassword, String newPin) {
        validatePin(newPin);
        Employee emp = getEmp(empId);
        if (!passwordEncoder.matches(loginPassword, emp.getEmpPassword())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }
        emp.updateSimplePassword(passwordEncoder.encode(newPin));
    }

    @Transactional
    public void change(Long empId, String currentPin, String newPin) {
        validatePin(newPin);
        Employee emp = getEmp(empId);
        if (emp.getSimplePassword() == null
                || !passwordEncoder.matches(currentPin, emp.getSimplePassword())) {
            throw new CustomException(ErrorCode.SIMPLE_PIN_MISMATCH);
        }
        emp.updateSimplePassword(passwordEncoder.encode(newPin));
    }

    @Transactional
    public void remove(Long empId, String loginPassword) {
        Employee emp = getEmp(empId);
        if (!passwordEncoder.matches(loginPassword, emp.getEmpPassword())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }
        emp.updateSimplePassword(null);
    }

    private Employee getEmp(Long empId) {
        return employeeRepository.findById(empId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));
    }

    private void validatePin(String pin) {
        if (pin == null || !pin.matches("\\d{4}")) {
            throw new CustomException(ErrorCode.INVALID_PIN_FORMAT);
        }
    }
}
