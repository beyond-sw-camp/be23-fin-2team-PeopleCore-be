package com.peoplecore.auth.service;

import com.peoplecore.auth.domain.FaceRegistration;
import com.peoplecore.auth.dto.*;
import com.peoplecore.auth.jwt.JwtProvider;
import com.peoplecore.auth.repository.FaceRegistrationRepository;
import com.peoplecore.employee.domain.EmpStatus;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FaceAuthService {

    private final FaceRecognitionClient faceRecognitionClient;
    private final EmployeeRepository employeeRepository;
    private final FaceRegistrationRepository faceRegistrationRepository;
    private final JwtProvider jwtProvider;
    private final StringRedisTemplate redisTemplate;

    private static final String REFRESH_TOKEN_PREFIX = "RT:";

    public FaceAuthService(
            FaceRecognitionClient faceRecognitionClient,
            EmployeeRepository employeeRepository,
            FaceRegistrationRepository faceRegistrationRepository,
            JwtProvider jwtProvider,
            @Qualifier("refreshTokenRedisTemplate") StringRedisTemplate redisTemplate) {
        this.faceRecognitionClient = faceRecognitionClient;
        this.employeeRepository = employeeRepository;
        this.faceRegistrationRepository = faceRegistrationRepository;
        this.jwtProvider = jwtProvider;
        this.redisTemplate = redisTemplate;
    }

    public FaceValidateResponse validateFace(String image) {
        FaceExtractResponse extracted =
                faceRecognitionClient.extractEmbedding(new FaceExtractRequest(image));
        return FaceValidateResponse.builder()
                .valid(true)
                .message(extracted.getMessage() != null ? extracted.getMessage() : "얼굴이 정상적으로 인식되었습니다.")
                .build();
    }

    @Transactional
    public FaceRegisterResponse registerFace(FaceRegisterRequest request) {
        Employee employee = employeeRepository.findById(request.getEmpId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사원입니다."));

        if (employee.getEmpStatus() == EmpStatus.RESIGNED) {
            throw new IllegalStateException("퇴직한 사원은 얼굴을 등록할 수 없습니다.");
        }

        UUID companyId = employee.getCompany().getCompanyId();

        FaceRegisterResponse response = faceRecognitionClient.registerFace(
                request.getImage(),
                employee.getEmpId(),
                employee.getEmpName(),
                companyId
        );

        FaceRegistration registration = faceRegistrationRepository
                .findByEmpId(employee.getEmpId())
                .map(existing -> FaceRegistration.builder()
                        .id(existing.getId())
                        .empId(employee.getEmpId())
                        .empName(employee.getEmpName())
                        .registeredAt(java.time.LocalDateTime.now())
                        .build())
                .orElse(FaceRegistration.builder()
                        .empId(employee.getEmpId())
                        .empName(employee.getEmpName())
                        .build());

        faceRegistrationRepository.save(registration);

        return response;
    }

    @Transactional(readOnly = true)
    public List<FaceEmployeeResponse> getUnregisteredEmployees(UUID companyId) {
        List<Employee> employees = employeeRepository.findAll().stream()
                .filter(e -> e.getCompany().getCompanyId().equals(companyId))
                .filter(e -> e.getEmpStatus() != EmpStatus.RESIGNED)
                .filter(e -> !faceRegistrationRepository.existsByEmpId(e.getEmpId()))
                .toList();

        return employees.stream()
                .map(e -> FaceEmployeeResponse.builder()
                        .empId(e.getEmpId())
                        .empName(e.getEmpName())
                        .empNum(e.getEmpNum())
                        .deptName(e.getDept().getDeptName())
                        .gradeName(e.getGrade().getGradeName())
                        .faceRegistered(false)
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FaceEmployeeResponse> getRegisteredEmployees(UUID companyId) {
        List<FaceRegistration> registrations = faceRegistrationRepository.findAll();
        Map<Long, FaceRegistration> regMap = registrations.stream()
                .collect(Collectors.toMap(FaceRegistration::getEmpId, Function.identity()));

        List<Employee> employees = employeeRepository.findAll().stream()
                .filter(e -> e.getCompany().getCompanyId().equals(companyId))
                .filter(e -> e.getEmpStatus() != EmpStatus.RESIGNED)
                .filter(e -> regMap.containsKey(e.getEmpId()))
                .toList();

        return employees.stream()
                .map(e -> FaceEmployeeResponse.builder()
                        .empId(e.getEmpId())
                        .empName(e.getEmpName())
                        .empNum(e.getEmpNum())
                        .deptName(e.getDept().getDeptName())
                        .gradeName(e.getGrade().getGradeName())
                        .faceRegistered(true)
                        .registeredAt(regMap.get(e.getEmpId()).getRegisteredAt())
                        .build())
                .toList();
    }

    @Transactional
    public void unregisterFace(Long empId) {
        Employee employee = employeeRepository.findById(empId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사원입니다."));
        UUID companyId = employee.getCompany().getCompanyId();

        // 1. Python Chroma DB에서 벡터 삭제 (회사 범위로 지정)
        try {
            faceRecognitionClient.unregisterFace(empId, companyId);
        } catch (Exception e) {
            // Python 서버 불가 등으로 실패하더라도 MySQL 이력은 정리한다.
            log.warn("Python 얼굴 벡터 삭제 실패 (empId={}, companyId={}): {}", empId, companyId, e.getMessage());
        }

        // 2. MySQL에서 등록 이력 삭제
        faceRegistrationRepository.findByEmpId(empId)
                .ifPresent(faceRegistrationRepository::delete);
    }

    /**
     * 사원 삭제/퇴직 시 외부 서비스에서 호출하는 cascade cleanup.
     * Python 실패 시에도 호출 측 트랜잭션을 끊지 않도록 예외를 삼킨다.
     */
    @Transactional
    public void cascadeUnregisterFace(Long empId, UUID companyId) {
        try {
            faceRecognitionClient.unregisterFace(empId, companyId);
        } catch (Exception e) {
            log.warn("cascade 얼굴 벡터 삭제 실패 (empId={}, companyId={}): {}", empId, companyId, e.getMessage());
        }
        faceRegistrationRepository.findByEmpId(empId)
                .ifPresent(faceRegistrationRepository::delete);
    }

    /**
     * 회사 단위 일괄 cleanup. 회사 EXPIRED/SUSPENDED 전이 시 호출한다.
     */
    @Transactional
    public void cascadeUnregisterCompany(UUID companyId) {
        try {
            faceRecognitionClient.unregisterCompanyFaces(companyId);
        } catch (Exception e) {
            log.warn("회사 얼굴 벡터 일괄 삭제 실패 (companyId={}): {}", companyId, e.getMessage());
        }
        List<Long> empIds = employeeRepository.findAll().stream()
                .filter(e -> e.getCompany().getCompanyId().equals(companyId))
                .map(Employee::getEmpId)
                .toList();
        if (!empIds.isEmpty()) {
            faceRegistrationRepository.findAll().stream()
                    .filter(r -> empIds.contains(r.getEmpId()))
                    .forEach(faceRegistrationRepository::delete);
        }
    }

    @Transactional
    public LoginResponse faceLogin(FaceLoginRequest request) {
        if (request.getCompanyId() == null) {
            throw new IllegalArgumentException("회사 ID가 필요합니다.");
        }

        // 1. Python 서버에 얼굴 인식 요청 (회사 범위로 제한)
        FaceRecognizeResponse recognizeResult =
                faceRecognitionClient.recognizeFace(request.getImage(), request.getCompanyId());

        // 2. 인식된 사원 조회
        Employee employee = employeeRepository.findById(recognizeResult.getEmpId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사원입니다."));

        // 3. 회사 소속 재검증 (Python 메타데이터와 별개로 Java 레벨에서 2차 확인)
        if (!employee.getCompany().getCompanyId().equals(request.getCompanyId())) {
            throw new IllegalStateException("요청한 회사에 소속된 사원이 아닙니다.");
        }

        // 4. 퇴직 여부 확인
        if (employee.getEmpStatus() == EmpStatus.RESIGNED) {
            throw new IllegalStateException("퇴직한 사원입니다.");
        }

        // 5. JWT 발급 (기존 AuthService.login과 동일한 로직)
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
                .build();
    }
}
