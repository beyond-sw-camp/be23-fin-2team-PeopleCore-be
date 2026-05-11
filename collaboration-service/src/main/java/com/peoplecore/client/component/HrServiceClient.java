package com.peoplecore.client.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.approval.dto.EmpDetailResponse;
import com.peoplecore.client.dto.AttendanceModifyHrMemberResDto;
import com.peoplecore.client.dto.CompanyInfoResponse;
import com.peoplecore.client.dto.DeptInfoResponse;
import com.peoplecore.client.dto.EmployeeSimpleResDto;
import com.peoplecore.client.dto.TitleInfoResponse;
import com.peoplecore.client.dto.VacationValidateRequest;
import com.peoplecore.event.VacationSlotItem;
import com.peoplecore.exception.BusinessException;
import com.peoplecore.exception.ErrorResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@Slf4j
public class HrServiceClient {
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public HrServiceClient(RestClient.Builder restClient, ObjectMapper objectMapper) {
        this.restClient = restClient.baseUrl("http://hr-service").build();
        this.objectMapper = objectMapper;
    }

    @CircuitBreaker(name = "hrService", fallbackMethod = "getDeptFallback")
    public DeptInfoResponse getDept(Long deptId) {
        return restClient.get()
                .uri("/internal/dept/{deptId}", deptId)
                .retrieve()
                .body(DeptInfoResponse.class);
    }

    // fallback 메서드 - 파라미터 동일 + Throwable 추가
    public DeptInfoResponse getDeptFallback(Long deptId, Throwable t) {
        log.warn("HR 서비스 부서 조회 실패 deptId: {}, error: {}", deptId, t.getMessage());
        throw new BusinessException("HR 서비스 연결 실패: 부서 정보를 조회할 수 없습니다.", HttpStatus.SERVICE_UNAVAILABLE);
    }

    @CircuitBreaker(name = "hrService", fallbackMethod = "getTitleFallback")
    public TitleInfoResponse getTitle(Long titleId) {
        return restClient.get()
                .uri("/internal/title/{titleId}", titleId)
                .retrieve()
                .body(TitleInfoResponse.class);
    }

    public TitleInfoResponse getTitleFallback(Long titleId, Throwable t) {
        log.warn("HR 서비스 직책 조회 실패 titleId: {}, error: {}", titleId, t.getMessage());
        throw new BusinessException("HR 서비스 연결 실패: 직책 정보를 조회할 수 없습니다.", HttpStatus.SERVICE_UNAVAILABLE);
    }

    @CircuitBreaker(name = "hrService", fallbackMethod = "getCompanyFallback")
    public CompanyInfoResponse getCompany(UUID companyId) {
        return restClient.get()
                .uri("/internal/companies/{companyId}", companyId)
                .retrieve()
                .body(CompanyInfoResponse.class);
    }


    public CompanyInfoResponse getCompanyFallback(UUID companyId, Throwable t) {
        log.warn("HR 서비스 회사 조회 실패 companyId: {}, error: {}", companyId, t.getMessage());
        throw new BusinessException("HR 서비스 연결 실패: 회사 정보를 조회할 수 없습니다.", HttpStatus.SERVICE_UNAVAILABLE);
    }

    @CircuitBreaker(name = "hrService", fallbackMethod = "getEmployeeBulkFallback")
    public List<EmployeeSimpleResDto> getEmployees(List<Long> empIds){
        String ids = empIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        return restClient.get()
                .uri("/internal/employee/bulk?empIds={ids}", ids)
                .retrieve()
                .body(new ParameterizedTypeReference<List<EmployeeSimpleResDto>>() {});
    }


    @CircuitBreaker(name = "hrService", fallbackMethod = "getEmployeeFallback")
    public EmpDetailResponse getEmployee(UUID companyId, Long empId) {
        return restClient.get()
                .uri("/internal/employee/{empId}", empId)
                .header("X-User-Company", companyId.toString())
                .retrieve()
                .body(EmpDetailResponse.class);
    }

    public EmpDetailResponse getEmployeeFallback(UUID companyId, Long empId, Throwable t) {
        log.warn("HR 서비스 사원 단건 조회 실패 empId={}, error={}", empId, t.getMessage());
        throw new BusinessException("HR 서비스 연결 실패: 사원 정보를 조회할 수 없습니다.", HttpStatus.SERVICE_UNAVAILABLE);
    }

    public List<EmployeeSimpleResDto> getEmployeeBulkFallback(List<Long> empIds, Throwable t){
        log.warn("HR 서비스 사원 조회 실패 empIds: {}, error: {}", empIds, t.getMessage());
        throw new BusinessException("HR 서비스 연결 실패: 사원정보를 조회할 수 없습니다.", HttpStatus.SERVICE_UNAVAILABLE);
    }

    /* 근태 정정 결재선 HR 검증용 — hr-service 의 HR_ADMIN/HR_SUPER_ADMIN 사원 목록 조회 */
    @CircuitBreaker(name = "hrService", fallbackMethod = "getHrMembersFallback")
    public AttendanceModifyHrMemberResDto getHrMembers(UUID companyId) {
        return restClient.get()
                .uri("/attendance/modify/hr-members")
                .header("X-User-Company", companyId.toString())
                .retrieve()
                .body(AttendanceModifyHrMemberResDto.class);
    }

    public AttendanceModifyHrMemberResDto getHrMembersFallback(UUID companyId, Throwable t) {
        log.warn("HR 서비스 인사팀 사원 조회 실패 companyId: {}, error: {}", companyId, t.getMessage());
        throw new BusinessException("HR 서비스 연결 실패: 인사팀 사원 정보를 조회할 수 없습니다.", HttpStatus.SERVICE_UNAVAILABLE);
    }

    /* 휴가 신청 사전 검증 - 결재 문서 생성 전 hr-service 동기 호출 */
    /* 4xx: hr-service 에러 본문에서 message 추출 → BusinessException 으로 전환 (실패 사유/상태 보존 → FE 노출) */
    /* 연결실패/5xx: fallback 에서 SERVICE_UNAVAILABLE BusinessException */
    @CircuitBreaker(name = "hrService", fallbackMethod = "validateVacationRequestFallback")
    public void validateVacationRequest(UUID companyId, Long empId, Long infoId, List<VacationSlotItem> items) {
        VacationValidateRequest body = VacationValidateRequest.builder()
                .empId(empId)
                .infoId(infoId)
                .items(items)
                .build();
        restClient.post()
                .uri("/internal/vacation/validate-request")
                .header("X-User-Company", companyId.toString())
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    throw translateHrError(res.getStatusCode(), res.getBody());
                })
                .toBodilessEntity();
    }

    /**
     * hr-service 4xx 응답을 BusinessException 으로 변환.
     * <p>
     * hr-service 의 GlobalExceptionHandler 가 던지는 본문 포맷이 핸들러마다 다름:
     * <ul>
     *   <li>{@code CustomException} → {@code ErrorResponse{status, code, message}}</li>
     *   <li>{@code IllegalArgumentException}/{@code IllegalStateException}/{@code BusinessException}
     *       → {@code {message, timestamp}}</li>
     *   <li>Spring framework 예외(HttpMessageNotReadable, MissingRequestHeader 등)
     *       → 기본 whitelabel {@code {timestamp, status, error, path}}</li>
     * </ul>
     * <p>
     * 따라서 ErrorResponse → Map → raw 텍스트 순서로 단계적으로 파싱해
     * 어떤 포맷이든 가능한 한 의미있는 message 를 FE 까지 전파한다.
     * 파싱이 모두 실패하면 raw body 의 첫 200자를 로그에 남기고 상태코드만 노출.
     */
    private BusinessException translateHrError(HttpStatusCode status, InputStream bodyStream) {
        HttpStatus fallbackStatus;
        try {
            fallbackStatus = HttpStatus.valueOf(status.value());
        } catch (IllegalArgumentException ignored) {
            // 알려지지 않은 4xx 코드 — 매우 드물지만 안전 폴백.
            fallbackStatus = HttpStatus.BAD_REQUEST;
        }

        byte[] bodyBytes;
        try (InputStream is = bodyStream) {
            bodyBytes = is == null ? new byte[0] : is.readAllBytes();
        } catch (IOException ioe) {
            log.warn("HR 서비스 응답 본문 읽기 실패 - status={}, err={}", status, ioe.getMessage());
            return new BusinessException("HR 서비스 검증 실패 (" + status.value() + ")", HttpStatus.BAD_GATEWAY);
        }

        // 1차: 표준 ErrorResponse 포맷 (CustomException 핸들러 출력) 시도.
        //      status>0 + message non-null 이면 정상 파싱으로 간주.
        try {
            ErrorResponse err = objectMapper.readValue(bodyBytes, ErrorResponse.class);
            if (err != null && err.getMessage() != null && !err.getMessage().isBlank()) {
                HttpStatus mapped;
                try {
                    mapped = err.getStatus() > 0 ? HttpStatus.valueOf(err.getStatus()) : fallbackStatus;
                } catch (IllegalArgumentException badStatus) {
                    mapped = fallbackStatus;
                }
                return new BusinessException(err.getMessage(), mapped);
            }
        } catch (IOException ignored) {
            // 폴백 경로로 진행 — 다른 핸들러 포맷일 수 있음.
        }

        // 2차: 일반 Map 으로 파싱해 message 추출. IllegalArgument/IllegalState/BusinessException 핸들러 출력 흡수.
        try {
            Map<String, Object> body = objectMapper.readValue(bodyBytes,
                    new TypeReference<Map<String, Object>>() {});
            Object msgObj = body.get("message");
            if (msgObj instanceof String msg && !msg.isBlank()) {
                return new BusinessException(msg, fallbackStatus);
            }
            // Spring 기본 whitelabel 본문은 error 필드에 사유가 들어옴.
            Object errObj = body.get("error");
            if (errObj instanceof String err && !err.isBlank()) {
                return new BusinessException(err, fallbackStatus);
            }
        } catch (IOException ignored) {
            // 폴백 — JSON 자체가 깨졌거나 빈 바디.
        }

        // 3차: 어떤 파싱에도 실패. raw 바디를 로그에 남기고 일반 메시지 노출.
        String raw = bodyBytes.length > 0 ? new String(bodyBytes, StandardCharsets.UTF_8) : "(empty body)";
        String trimmed = raw.length() > 200 ? raw.substring(0, 200) + "..." : raw;
        log.warn("HR 서비스 4xx 응답 파싱 불가 - status={}, body={}", status, trimmed);
        return new BusinessException("HR 서비스 검증 실패 (" + status.value() + ")", fallbackStatus);
    }

    /* fallback - 비즈니스 거부(BusinessException) 는 그대로 전파, 연결실패/5xx 만 SERVICE_UNAVAILABLE 로 변환 */
    /* CircuitBreaker open 우려: 잔여 부족 등 정상 거부도 실패 카운트되나 기존 메서드들과 동일 정책 유지 */
    public void validateVacationRequestFallback(UUID companyId, Long empId, Long infoId,
                                                 List<VacationSlotItem> items, Throwable t) {
        if (t instanceof BusinessException be) {
            throw be;
        }
        log.warn("HR 서비스 휴가 신청 검증 실패 empId={}, error={}", empId, t.getMessage());
        throw new BusinessException("HR 서비스 연결 실패: 휴가 신청 검증을 수행할 수 없습니다.",
                HttpStatus.SERVICE_UNAVAILABLE);
    }
}
