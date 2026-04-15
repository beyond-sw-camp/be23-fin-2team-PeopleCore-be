package com.peoplecore.auth.service;

import com.peoplecore.auth.dto.HrAdminPinDtos;
import com.peoplecore.auth.jwt.JwtProvider;
import com.peoplecore.employee.domain.EmpRole;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class HrAdminPinService {

    private static final long HR_ADMIN_SCOPE_TTL_SECONDS = 30 * 60L;
    private static final Pattern PIN_PATTERN = Pattern.compile("^\\d{4,6}$");

    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    @Transactional(readOnly = true)
    public HrAdminPinDtos.StatusResponse getStatus(Long empId) {
        Employee emp = requireSuperAdmin(empId);
        return new HrAdminPinDtos.StatusResponse(
                emp.getHrAdminPinHash() != null,
                emp.getHrAdminPinUpdatedAt()
        );
    }

    @Transactional
    public void setPin(Long empId, HrAdminPinDtos.SetRequest req) {
        Employee emp = requireSuperAdmin(empId);
        if (emp.getHrAdminPinHash() != null) {
            throw new CustomException(ErrorCode.HR_ADMIN_PIN_ALREADY_SET);
        }
        if (!passwordEncoder.matches(req.getLoginPassword(), emp.getEmpPassword())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }
        validatePinFormat(req.getNewPin());
        emp.updateHrAdminPin(passwordEncoder.encode(req.getNewPin()));
    }

    @Transactional
    public void changePin(Long empId, HrAdminPinDtos.ChangeRequest req) {
        Employee emp = requireSuperAdmin(empId);
        if (emp.getHrAdminPinHash() == null) {
            throw new CustomException(ErrorCode.HR_ADMIN_PIN_NOT_SET);
        }
        if (!passwordEncoder.matches(req.getCurrentPin(), emp.getHrAdminPinHash())) {
            throw new CustomException(ErrorCode.HR_ADMIN_PIN_MISMATCH);
        }
        validatePinFormat(req.getNewPin());
        emp.updateHrAdminPin(passwordEncoder.encode(req.getNewPin()));
    }

    @Transactional
    public void deletePin(Long empId, HrAdminPinDtos.DeleteRequest req) {
        Employee emp = requireSuperAdmin(empId);
        if (!passwordEncoder.matches(req.getLoginPassword(), emp.getEmpPassword())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }
        emp.clearHrAdminPin();
    }

    @Transactional(readOnly = true)
    public HrAdminPinDtos.VerifyResponse verifyPin(Long empId, HrAdminPinDtos.VerifyRequest req) {
        Employee emp = requireSuperAdmin(empId);
        if (emp.getHrAdminPinHash() == null) {
            throw new CustomException(ErrorCode.HR_ADMIN_PIN_NOT_SET);
        }
        if (!passwordEncoder.matches(req.getPin(), emp.getHrAdminPinHash())) {
            throw new CustomException(ErrorCode.HR_ADMIN_PIN_MISMATCH);
        }
        String token = jwtProvider.createHrAdminScopeToken(empId);
        return new HrAdminPinDtos.VerifyResponse(token, HR_ADMIN_SCOPE_TTL_SECONDS);
    }

    private Employee requireSuperAdmin(Long empId) {
        Employee emp = employeeRepository.findById(empId)
                .orElseThrow(() -> new CustomException(ErrorCode.EMPLOYEE_NOT_FOUND));
        if (emp.getEmpRole() != EmpRole.HR_SUPER_ADMIN) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        return emp;
    }

    private void validatePinFormat(String pin) {
        if (pin == null || !PIN_PATTERN.matcher(pin).matches()) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }
    }
}
