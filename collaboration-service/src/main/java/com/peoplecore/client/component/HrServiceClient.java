package com.peoplecore.client.component;

import com.peoplecore.client.dto.AttendanceModifyHrMemberResDto;
import com.peoplecore.client.dto.CompanyInfoResponse;
import com.peoplecore.client.dto.DeptInfoResponse;
import com.peoplecore.client.dto.EmployeeSimpleResDto;
import com.peoplecore.client.dto.TitleInfoResponse;
import com.peoplecore.exception.BusinessException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@Slf4j
public class HrServiceClient {
    private final RestClient restClient;

    @Autowired
    public HrServiceClient(RestClient.Builder restClient) {
        this.restClient = restClient.baseUrl("http://hr-service").build();
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
}
