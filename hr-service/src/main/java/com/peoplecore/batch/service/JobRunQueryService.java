package com.peoplecore.batch.service;

import com.peoplecore.batch.dto.JobRunResDto;
import com.peoplecore.batch.repository.JobRunQueryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;

/* 잡 실행 이력 조회 Service - 운영자 대시보드용 read-only 진입점 */
@Service
@Slf4j
public class JobRunQueryService {

    private final JobRunQueryRepository jobRunQueryRepository;

    @Autowired
    public JobRunQueryService(JobRunQueryRepository jobRunQueryRepository) {
        this.jobRunQueryRepository = jobRunQueryRepository;
    }

    /* 검색 - 페이징. fromDate inclusive / toDate inclusive (Repository 가 +1일 변환) */
    public Page<JobRunResDto> search(String jobName, String companyId,
                                     LocalDate fromDate, LocalDate toDate,
                                     String status, Pageable pageable) {
        return jobRunQueryRepository.search(jobName, companyId, fromDate, toDate, status, pageable);
    }

    /* 단건 - 미존재 시 Optional.empty (Controller 가 404 처리) */
    public Optional<JobRunResDto> findOne(Long jobExecutionId) {
        return Optional.ofNullable(jobRunQueryRepository.findOne(jobExecutionId));
    }
}
