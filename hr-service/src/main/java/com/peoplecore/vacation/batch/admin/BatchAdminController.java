package com.peoplecore.vacation.batch.admin;

import com.peoplecore.auth.RoleRequired;
import com.peoplecore.vacation.batch.admin.dto.BatchExecutionResponse;
import com.peoplecore.vacation.batch.admin.dto.BatchRerunRequest;
import com.peoplecore.vacation.batch.admin.dto.BatchRerunResponse;
import com.peoplecore.vacation.batch.admin.dto.DiscordTestRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/* 배치 실행 관리 API - HR_SUPER_ADMIN 전용 */
@RestController
@RequestMapping("/api/admin/batch")
public class BatchAdminController {

    private final BatchAdminService batchAdminService;

    @Autowired
    public BatchAdminController(BatchAdminService batchAdminService) {
        this.batchAdminService = batchAdminService;
    }

    /* 최근 실행 이력 조회 - 본인 회사 JobParameters 와 일치하는 이력만. balanceExpiryJob(전사 통합) 자동 제외 */
    @RoleRequired({"HR_SUPER_ADMIN"})
    @GetMapping("/executions")
    public ResponseEntity<List<BatchExecutionResponse>> listExecutions(
            @RequestHeader("X-User-Company") UUID callerCompanyId,
            @RequestParam(required = false) String jobName,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(batchAdminService.listRecent(jobName, limit, callerCompanyId));
    }

    /* 재실행 트리거 - 본인 회사 건만 가능. balanceExpiryJob 은 전사 통합이라 금지(BATCH_TENANT_FORBIDDEN) */
    /* mode 미지정 시 RESTART. 이미 COMPLETED 면 FRESH 자동 승격 */
    @RoleRequired({"HR_SUPER_ADMIN"})
    @PostMapping("/{jobName}/rerun")
    public ResponseEntity<BatchRerunResponse> rerun(
            @PathVariable String jobName,
            @RequestHeader("X-User-Company") UUID callerCompanyId,
            @RequestBody BatchRerunRequest request) {
        return ResponseEntity.ok(batchAdminService.rerun(jobName, request, callerCompanyId));
    }

    /* Discord 웹훅 단독 테스트 - DiscordNotifier.notifyBatchFailure 를 페이크 파라미터로 호출 */
    /* 배치 메타 DB 오염 없음. 202 Accepted 반환(비동기 전송이라 성공 여부는 서버 로그/Discord 채널 확인) */
    // TODO(배포 전): 운영 환경에는 노출 금지 - @Profile("!prod") 로 제한하거나 해당 메서드 제거
    //                 개발/스테이징 스모크 테스트 전용 엔드포인트
    @RoleRequired({"HR_SUPER_ADMIN"})
    @PostMapping("/test-discord")
    public ResponseEntity<Void> testDiscord(@RequestBody(required = false) DiscordTestRequest request) {
        batchAdminService.sendTestDiscordAlert(request != null ? request : new DiscordTestRequest());
        return ResponseEntity.accepted().build();
    }
}
