package com.peoplecore.client.component;

import com.peoplecore.client.dto.CompanyInfoResponse;
import com.peoplecore.client.dto.DeptInfoResponse;
import com.peoplecore.client.dto.EmployeeSimpleResDto;
import com.peoplecore.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class HrCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final HrServiceClient hrServiceClient;

    private static final String DEPT_KEY = "hr:dept:";
    private static final String COMPANY_KEY = "hr:company:";
    private static final Duration TTL = Duration.ofHours(1);
    private static final String EMP_KEY = "hr:emp:";

    @Autowired
    public HrCacheService(
            @Qualifier("hrCacheRedisTemplate") RedisTemplate<String, Object> redisTemplate,
            HrServiceClient hrServiceClient) {
        this.redisTemplate = redisTemplate;
        this.hrServiceClient = hrServiceClient;
    }

    public DeptInfoResponse getDept(Long deptId) {
        String key = DEPT_KEY + deptId;

        // 먼저 Redis 캐시 조회
        try {
            DeptInfoResponse cached = (DeptInfoResponse) redisTemplate.opsForValue().get(key);
            if (cached != null) return cached;
        } catch (Exception e) {
            log.warn("Redis 조회 실패, HR 서비스 직접 호출 deptId={}, error={}", deptId, e.getMessage());
        }

        //  HR 서비스 호출
        DeptInfoResponse response;
        try {
            response = hrServiceClient.getDept(deptId);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("HR 서비스 부서 호출 중 예상치 못한 오류 deptId={}, error={}", deptId, e.getMessage());
            throw new BusinessException("HR 서비스 연결 실패: 부서 정보를 조회할 수 없습니다.", HttpStatus.SERVICE_UNAVAILABLE);
        }

        //Redis에 캐싱
        try {
            redisTemplate.opsForValue().set(key, response, TTL);
        } catch (Exception e) {
            log.warn("Redis 저장 실패 - 채번은 계속 진행 deptId={}, error={}", deptId, e.getMessage());
        }

        return response;
    }

    public CompanyInfoResponse getCompany(UUID companyId) {
        String key = COMPANY_KEY + companyId;
        // 먼저 Redis 캐시 조회
        try {
            CompanyInfoResponse cached = (CompanyInfoResponse) redisTemplate.opsForValue().get(key);
            if (cached != null) return cached;
        } catch (Exception e) {
            log.warn("Redis 조회 실패, HR 서비스 직접 호출 companyId={}, error={}", companyId, e.getMessage());
        }

        //  HR 서비스 호출
        CompanyInfoResponse response;
        try {
            response = hrServiceClient.getCompany(companyId);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("HR 서비스 회사 호출 중 예상치 못한 오류 companyId={}, error={}", companyId, e.getMessage());
            throw new BusinessException("HR 서비스 연결 실패: 회사 정보를 조회할 수 없습니다.", HttpStatus.SERVICE_UNAVAILABLE);
        }

        //Redis에 캐싱
        try {
            redisTemplate.opsForValue().set(key, response, TTL);
        } catch (Exception e) {
            log.warn("Redis 저장 실패 - 채번은 계속 진행 companyId={}, error={}", companyId, e.getMessage());
        }

        return response;
    }

    public void evictDept(Long deptId) {
        try {
            redisTemplate.delete(DEPT_KEY + deptId);
            log.info("부서 캐시 무효화 deptId={}", deptId);
        } catch (Exception e) {
            log.warn("부서 캐시 무효화 실패 deptId={}", deptId);
        }
    }

    public void evictCompany(UUID companyId) {
        try {
            redisTemplate.delete(COMPANY_KEY + companyId);
            log.info("회사 캐시 무효화 companyId={}", companyId);
        } catch (Exception e) {
            log.warn("회사 캐시 무효화 실패 companyId={}", companyId);
        }
    }

//    사원정보 일괄 조회
    public List<EmployeeSimpleResDto> getEmployees(List<Long> empIds){
        List<EmployeeSimpleResDto> result = new ArrayList<>();
        List<Long> missedIds = new ArrayList<>();

//        1. redis에서 개별캐시 조회
        for(Long empId : empIds){
            try{
                EmployeeSimpleResDto cached = (EmployeeSimpleResDto) redisTemplate.opsForValue().get(EMP_KEY + empId);
                if (cached != null){
                    result.add(cached);
                    continue;
                }
            } catch (Exception e){
                log.warn("Redis 조회 실패 empId={}, error={}", empId, e.getMessage());
            }
            missedIds.add(empId);
        }

//        2. 캐시 miss분 hr-service 호출
        if(!missedIds.isEmpty()){
            List<EmployeeSimpleResDto> fetched = hrServiceClient.getEmployees(missedIds);

//            3. 호출 결과 캐싱 후 결과 병합
            for (EmployeeSimpleResDto emp : fetched){
                try {
                    redisTemplate.opsForValue().set(EMP_KEY +emp.getEmpId(), emp, TTL);
                } catch (Exception e){
                    log.warn("Redis 저장 실패 empId={}, error={}", emp.getEmpId(), e.getMessage());
                }
                result.add(emp);
            }
        }
        return result;
    }

    public void evictEmployee(Long empId){
        try{
            redisTemplate.delete(EMP_KEY + empId);
            log.info("사원 캐시 무효화 empId={}", empId);
        } catch (Exception e){
            log.warn("사원 캐시 무효화 실패 empId={}", empId);
        }
    }


}