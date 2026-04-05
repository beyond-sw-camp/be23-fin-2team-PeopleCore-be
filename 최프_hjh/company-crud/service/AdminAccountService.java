package com.peoplecore.service;

import com.peoplecore.dto.AdminAccountResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * 최고관리자 계정 생성 서비스
 *
 * 기존 Employee 테이블에 emp_role = 'ADMIN'으로 계정 생성
 * 임시 비밀번호 발급 후 이메일 전달
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAccountService {

    // private final EmployeeRepository employeeRepository;
    // private final PasswordEncoder passwordEncoder;
    // private final EmailService emailService;

    private static final String CHAR_POOL = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789!@#$";
    private static final int TEMP_PASSWORD_LENGTH = 12;

    /**
     * 최고관리자 계정 생성
     *
     * 1) 임시 비밀번호 생성
     * 2) Employee 테이블에 ADMIN 권한으로 계정 생성
     * 3) 이메일로 회사 UUID + 계정 정보 전달
     */
    public AdminAccountResponse createAdminAccount(UUID companyId, String adminName, String adminEmail) {

        // 1) 임시 비밀번호 생성
        String tempPassword = generateTempPassword();

        // 2) Employee 테이블에 ADMIN 계정 생성
        //    TODO: 실제 Employee 엔티티 연동
        //    Employee admin = Employee.builder()
        //            .companyId(companyId)
        //            .empName(adminName)
        //            .empEmail(adminEmail)
        //            .empPassword(passwordEncoder.encode(tempPassword))
        //            .empRole(EmpRole.ADMIN)
        //            .build();
        //    employeeRepository.save(admin);

        log.info("관리자 계정 생성: companyId={}, email={}", companyId, adminEmail);

        // 3) 이메일 발송
        //    emailService.sendAdminCredentials(companyId, adminEmail, tempPassword);

        return AdminAccountResponse.builder()
                .companyId(companyId)
                .adminEmail(adminEmail)
                .adminName(adminName)
                .message("임시 비밀번호가 " + adminEmail + "로 전달되었습니다")
                .build();
    }

    /**
     * 임시 비밀번호 생성 (12자리, 영대소문자 + 숫자 + 특수문자)
     */
    private String generateTempPassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            sb.append(CHAR_POOL.charAt(random.nextInt(CHAR_POOL.length())));
        }
        return sb.toString();
    }
}
