package com.peoplecore.auth.service;

import com.peoplecore.auth.dto.LoginRequest;
import com.peoplecore.auth.dto.LoginResponse;
import com.peoplecore.auth.dto.TokenRefreshRequest;
import com.peoplecore.auth.jwt.JwtProvider;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.domain.EmpStatus;
import com.peoplecore.employee.repository.EmployeeRepository;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
public class AuthService {

    private final EmployeeRepository employeeRepository;
    private final JwtProvider jwtProvider;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;

    public AuthService(
            EmployeeRepository employeeRepository,
            JwtProvider jwtProvider,
            PasswordEncoder passwordEncoder,
            @Qualifier("refreshTokenRedisTemplate") StringRedisTemplate redisTemplate) {
        this.employeeRepository = employeeRepository;
        this.jwtProvider = jwtProvider;
        this.passwordEncoder = passwordEncoder;
        this.redisTemplate = redisTemplate;
    }

    private static final String REFRESH_TOKEN_PREFIX = "RT:";

    @Transactional
    public LoginResponse login(LoginRequest request) {
        Employee employee = employeeRepository
                .findByCompany_CompanyIdAndEmpEmail(request.getCompanyId(), request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("이메일 또는 비밀번호가 일치하지 않습니다."));

        if (employee.getEmpStatus() == EmpStatus.RESIGNED) {
            throw new IllegalStateException("퇴직한 사원입니다.");
        }

        if (!passwordEncoder.matches(request.getPassword(), employee.getEmpPassword())) {
            throw new IllegalArgumentException("이메일 또는 비밀번호가 일치하지 않습니다.");
        }

        String accessToken = jwtProvider.createAccessToken(employee);
        String refreshToken = jwtProvider.createRefreshToken(employee);

        redisTemplate.opsForValue().set(
                REFRESH_TOKEN_PREFIX + employee.getEmpId(),
                refreshToken,
                7, TimeUnit.DAYS
        );

        employee.updateLastLoginAt();

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .empName(employee.getEmpName())
                .empRole(employee.getEmpRole().name())
//                .mustChangePassword(employee.getMustChangePassword())
                .build();
    }

    @Transactional
    public LoginResponse refresh(TokenRefreshRequest request) {
        if (!jwtProvider.validateRefreshToken(request.getRefreshToken())) {
            throw new IllegalArgumentException("유효하지 않은 리프레시 토큰입니다.");
        }

        Claims claims = jwtProvider.parseRefreshToken(request.getRefreshToken());
        Long empId = Long.valueOf(claims.getSubject());

        String stored = redisTemplate.opsForValue().get(REFRESH_TOKEN_PREFIX + empId);
        if (stored == null || !stored.equals(request.getRefreshToken())) {
            throw new IllegalArgumentException("만료되었거나 이미 사용된 리프레시 토큰입니다.");
        }

        Employee employee = employeeRepository.findById(empId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사원입니다."));

        String newAccessToken = jwtProvider.createAccessToken(employee);
        String newRefreshToken = jwtProvider.createRefreshToken(employee);

        redisTemplate.opsForValue().set(
                REFRESH_TOKEN_PREFIX + empId,
                newRefreshToken,
                7, TimeUnit.DAYS
        );

        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .empName(employee.getEmpName())
                .empRole(employee.getEmpRole().name())
                .build();
    }

    public void logout(Long empId) {
        redisTemplate.delete(REFRESH_TOKEN_PREFIX + empId);
    }

    // 현재 로그인 유저 비밀번호 재확인 — 민감 액션(단계 개폐/기간 변경) 전 본인확인
    // 부작용 없음: 토큰/Redis/lastLoginAt 변경하지 않고 매칭 결과만 반환
    @Transactional(readOnly = true)
    public boolean verifyPassword(Long empId, String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) return false;
        Employee employee = employeeRepository.findById(empId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사원입니다."));
        return passwordEncoder.matches(rawPassword, employee.getEmpPassword());
    }
}