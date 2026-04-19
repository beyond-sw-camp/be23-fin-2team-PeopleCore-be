package com.peoplecore.auth.service;

import com.peoplecore.auth.domain.FaceRegistration;
import com.peoplecore.auth.dto.*;
import com.peoplecore.auth.jwt.JwtProvider;
import com.peoplecore.auth.repository.FaceRegistrationRepository;
import com.peoplecore.employee.domain.EmpStatus;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
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

        FaceRegisterResponse response = faceRecognitionClient.registerFace(
                request.getImage(),
                employee.getEmpId(),
                employee.getEmpName()
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
        // 1. Python Chroma DB에서 벡터 삭제
        faceRecognitionClient.unregisterFace(empId);

        // 2. MySQL에서 등록 이력 삭제
        faceRegistrationRepository.findByEmpId(empId)
                .ifPresent(faceRegistrationRepository::delete);
    }

    @Transactional
    public LoginResponse faceLogin(FaceLoginRequest request) {
        // 1. Python 서버에 얼굴 인식 요청
        FaceRecognizeResponse recognizeResult = faceRecognitionClient.recognizeFace(request.getImage());

        // 2. 인식된 사원 조회
        Employee employee = employeeRepository.findById(recognizeResult.getEmpId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사원입니다."));

        // 3. 퇴직 여부 확인
        if (employee.getEmpStatus() == EmpStatus.RESIGNED) {
            throw new IllegalStateException("퇴직한 사원입니다.");
        }

        // 4. JWT 발급 (기존 AuthService.login과 동일한 로직)
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
