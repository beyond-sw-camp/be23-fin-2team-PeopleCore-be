package com.peoplecore.client.component;

import com.peoplecore.client.dto.CompanyInfoResponse;
import com.peoplecore.client.dto.DeptInfoResponse;
import com.peoplecore.exception.BusinessException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

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
}
