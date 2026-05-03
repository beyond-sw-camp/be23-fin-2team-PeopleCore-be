package com.peoplecore.batch.controller;

import com.peoplecore.auth.RoleRequired;
import com.peoplecore.batch.dto.JobRunResDto;
import com.peoplecore.batch.service.JobRunQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/*
 * Spring Batch 잡 실행 이력 조회 Controller - 운영자 대시보드용.
 *
 * 권한: HR_SUPER_ADMIN 단독 (잡 운영 정보는 최고 권한자만 열람).
 *
 * 엔드포인트:
 *  - GET /admin/jobs/runs              : 검색 (페이징 + 다중 필터)
 *  - GET /admin/jobs/runs/{id}         : 단건 상세 (Step 카운트 포함)
 */
@RestController
@RequestMapping("/admin/jobs/runs")
@Slf4j
public class AdminJobRunController {

    private final JobRunQueryService jobRunQueryService;

    @Autowired
    public AdminJobRunController(JobRunQueryService jobRunQueryService) {
        this.jobRunQueryService = jobRunQueryService;
    }

    /*
     * 잡 실행 검색 - 모든 필터 nullable.
     *  jobName: 잡 이름 (예: monthlyAccrualJob)
     *  companyId: 회사 ID (UUID 문자열). 전사 통합 잡은 매칭 안 됨
     *  fromDate / toDate: 실행 일자 범위 inclusive (yyyy-MM-dd)
     *  status: COMPLETED / FAILED / STOPPED / ABANDONED 등
     *  페이징 기본: page=0, size=20. 정렬 고정 (create_time DESC)
     */
    @RoleRequired({"HR_SUPER_ADMIN"})
    @GetMapping
    public ResponseEntity<Page<JobRunResDto>> search(
            @RequestParam(required = false) String jobName,
            @RequestParam(required = false) String companyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String status,
            Pageable pageable) {
        return ResponseEntity.ok(jobRunQueryService.search(
                jobName, companyId, fromDate, toDate, status, pageable));
    }

    /* 단건 상세 - JobParameters + Step 카운트 포함. 미존재 시 404 */
    @RoleRequired({"HR_SUPER_ADMIN"})
    @GetMapping("/{jobExecutionId}")
    public ResponseEntity<JobRunResDto> findOne(@PathVariable Long jobExecutionId) {
        return jobRunQueryService.findOne(jobExecutionId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
