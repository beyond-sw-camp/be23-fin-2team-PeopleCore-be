package com.peoplecore.client.component;

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

import java.util.List;
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
    /* 4xx: ErrorResponse 파싱해 BusinessException 으로 전환 (실패 사유/상태 보존 → FE 노출) */
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
                    // hr-service ErrorResponse 파싱 - 사유/상태 그대로 보존해 FE 까지 전파
                    try {
                        ErrorResponse err = objectMapper.readValue(res.getBody(), ErrorResponse.class);
                        throw new BusinessException(err.getMessage(), HttpStatus.valueOf(err.getStatus()));
                    } catch (java.io.IOException ioe) {
                        throw new BusinessException("HR 서비스 응답 파싱 실패", HttpStatus.BAD_GATEWAY);
                    }
                })
                .toBodilessEntity();
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
