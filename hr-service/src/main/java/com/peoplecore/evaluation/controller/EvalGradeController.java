package com.peoplecore.evaluation.controller;

import com.peoplecore.evaluation.domain.EvalGradeSortField;
import com.peoplecore.evaluation.dto.*;
import com.peoplecore.evaluation.service.EvalGradeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// 등급 - 자동 산정 / 강제배분 / 보정 / 확정 / 조회
@RestController
@RequestMapping("/eval/grades")
public class EvalGradeController {

    private final EvalGradeService gradeService;


    public EvalGradeController(EvalGradeService gradeService) {
        this.gradeService = gradeService;
    }


    // ─── 자동 산정 페이지 ─────────────────────────

    // 1. 사원 목록 (페이징/필터/검색/정렬)
    //  - 응답: 사번/이름/부서/직급/종합점수(totalScore)/자동등급(autoGrade)
    //  - DB의 EvalGrade.totalScore + autoGrade 그대로 반환 (재계산 X)
    @GetMapping("/{seasonId}/list/draft")
    public ResponseEntity<Page<DraftListItemDto>> getDraftList(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long seasonId,
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) EvalGradeSortField sortField,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                gradeService.getDraftList(companyId, seasonId, deptId, keyword, sortField, pageable)
        );
    }


    // 2. 종합점수 계산 (수동 버튼 / 스케줄러 공통)
    //  - 자기 + 팀장 가중평균 + 조정점수 -> totalScore 저장
    //  - 자기/팀장 미제출자는 스킵 (row NULL 유지)
    //  - autoGrade 는 3번 강제배분에서 부여
    @PostMapping("/{seasonId}/calculate")
    public ResponseEntity<Void> calculate(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long seasonId) {
        gradeService.calculateAutoGrades(companyId, seasonId);
        return ResponseEntity.noContent().build();
    }


    // 3. 2,5 Z-score 편향보정 적용 (수동 버튼 / 스케줄러)
    //  - rules.useBiasAdjustment=false 면 biasAdjustedScore = totalScore 복사만
    //  - true 면 팀별 평균/표편 기반 Z-score 보정 점수 저장
    //  - 3번 applyDistribution 은 biasAdjustedScore 기준 랭킹
    @PostMapping("/{seasonId}/bias-adjust/apply")
    public ResponseEntity<Void> applyBiasAdjustment(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long seasonId) {
        gradeService.applyBiasAdjustment(companyId, seasonId);
        return ResponseEntity.noContent().build();
    }


    // 4. 등급 보정 검토 대상 조회 (GradeCalibration 화면 진입 시 호출)
    //  - 편향보정 스킵된 팀 (전원 동점 / 소규모) - Z-score 보정 불가 팀
    //  - 자기평가 scaleTo 초과로 clip 된 사원 - 알림
    @GetMapping("/{seasonId}/calibration/review")
    public ResponseEntity<CalibrationReviewDto> getCalibrationReview(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long seasonId) {
        return ResponseEntity.ok(gradeService.getCalibrationReview(companyId, seasonId));
    }


    // 5. 강제배분 적용-보정전 (수동 버튼+프론트에서 2,3호출 / 스케줄러 공통 /)
    //  - 시즌 전체 totalScore 내림차순 랭킹
    //  - 규칙의 ratio 대로 상위부터 autoGrade 부여 (마지막 등급은 잔여 할당)
    //  - 동점 시 비율산정>가감
    //  - 재실행 시: cohort 변화 없으면 no-op, 변화 있고 보정 이력 있으면 ?confirm=true 재호출 필요
    @PostMapping("/{seasonId}/distribution/apply")
    public ResponseEntity<DistributionApplyResultDto> applyDistribution(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long seasonId,
            @RequestParam(defaultValue = "false") boolean confirm) {
        return ResponseEntity.ok(
                gradeService.applyDistribution(companyId, seasonId, confirm)
        );
    }


    // 6. 팀장 편향 보정(Z-score) 팀별 효과 요약 -(자동 산정 화면 차트용)
    //  - 부서별 managerScore 평균(보정 전) / managerScoreAdjusted 평균(보정 후) + 인원 수
    //  - 응답에 minTeamSize 동봉 → 프론트에서 "소규모" 제외 판정에 사용
    @GetMapping("/{seasonId}/team-bias")
    public ResponseEntity<TeamBiasResponseDto> getTeamBias(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long seasonId) {
        return ResponseEntity.ok(gradeService.getTeamBiasSummary(companyId, seasonId));
    }


    // ─── 등급 보정 페이지 ─────────────────────────

    // 7. 실제 vs 목표 분포 + 보정 건수
    //  - 페이지 상단 카드 5개 + "N개 등급 불일치" 배지 + "현재 보정 건수 N건"
    @GetMapping("/{seasonId}/distribution-diff")
    public ResponseEntity<DistributionDiffDto> getDistributionDiff(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long seasonId) {
        return ResponseEntity.ok(gradeService.getDistributionDiff(companyId, seasonId));
    }


    // 8. 보정 페이지 사원 목록
    @GetMapping("/{seasonId}/list/calibration")
    public ResponseEntity<Page<CalibrationListItemDto>> getCalibrationList(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long seasonId,
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) EvalGradeSortField sortField,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                gradeService.getCalibrationList(companyId, seasonId, deptId, keyword, sortField, pageable)
        );
    }


    // 9. 보정 이력
    //  autoGrade -> adjustedGrade
    @GetMapping("/{seasonId}/calibrations")
    public ResponseEntity<List<CalibrationHistoryDto>> getCalibrations(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long seasonId) {
        return ResponseEntity.ok(gradeService.getCalibrations(companyId, seasonId));
    }


    // 10. 일괄 보정 저장
    //  - 사용자가 누적한 변경 N건 한 번에 전송 -> 서버에서 비율 검증 -> 통과 시 저장
    //  - Calibration 이력도 함께 INSERT
    //  - 비율 불일치 시 400 + currentDiff 반환 (저장 X)
    @PostMapping("/{seasonId}/calibration/batch")
    public ResponseEntity<CalibrationBatchResultDto> batchSaveCalibration(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Emp") Long adjusterEmpId,
            @PathVariable Long seasonId,
            @RequestBody List<CalibrationItemRequest> items) {
        return ResponseEntity.ok(
                gradeService.batchSaveCalibration(companyId, adjusterEmpId, seasonId, items)
        );
    }


    // ─── 최종 등급 확정 및 잠금 페이지 ─────────────────

    // 11. 상단 요약 지표 - 배정/미산정/보정 인원 + 잠금 상태
    @GetMapping("/{seasonId}/finalize/summary")
    public ResponseEntity<FinalizeSummaryDto> getFinalizeSummary(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long seasonId) {
        return ResponseEntity.ok(gradeService.getFinalizeSummary(companyId, seasonId));
    }


    // 12. 미제출·미산정 직원 목록 (finalGrade IS NULL 대상, 부서필터/정렬/페이징)
    @GetMapping("/{seasonId}/finalize/unassigned")
    public ResponseEntity<Page<UnassignedEmployeeDto>> getUnassignedList(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long seasonId,
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) EvalGradeSortField sortField,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(
                gradeService.getUnassignedList(companyId, seasonId, deptId, sortField, pageable)
        );
    }


    // 13. 최종 확정 및 잠금 - body.acknowledgedEmpIds 로 미산정자 전원 "제외 확정" 검증
    @PostMapping("/{seasonId}/finalize")
    public ResponseEntity<FinalizeDto> finalize(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Emp") Long adjusterEmpId,
            @PathVariable Long seasonId,
            @RequestBody FinalizeDto request) { //실제 미산정 대상인지 체크용
        return ResponseEntity.ok(
                gradeService.finalize(companyId, adjusterEmpId, seasonId, request)
        );
    }



    // ─── 평가 결과 조회 페이지 ─────────────────────────

    // 14. 평가 결과 목록 (HR 전용, 확정 전/후 모두 조회, 미산정자 포함)
    //  - 필터: deptId, keyword(이름/사번), unscoredOnly(null=전체 / true=미산정자만 / false=산정자만)
    //  - 시즌 상태 배너는 프론트에서 시즌 상세 조회로 분기 (확정 전 잠정 / 확정 후 잠금)
    @GetMapping("/{seasonId}/list/final")
    public ResponseEntity<Page<FinalGradeListItemDto>> getFinalList(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long seasonId,
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean unscoredOnly,
            @RequestParam(required = false) EvalGradeSortField sortField,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                gradeService.getFinalList(companyId, seasonId, deptId, keyword, unscoredOnly, sortField, pageable)
        );
    }
    // ─── 본인 평가결과 조회 (사원용) ─────────────────

    // 15. 본인 평가결과 - 드롭다운용 시즌 목록
    @GetMapping("/my/seasons")
    public ResponseEntity<List<MySeasonOptionDto>> getMySeasons(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId) {
        return ResponseEntity.ok(gradeService.getMySeasons(companyId, empId));
    }


    // 16. 본인 평가결과 - 특정 시즌 상세
    @GetMapping("/my/result")
    public ResponseEntity<MyEvalResultDto> getMyResult(
            @RequestHeader("X-User-Company") UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestParam Long seasonId) {
        return ResponseEntity.ok(gradeService.getMyResult(companyId, empId, seasonId));
    }


    // 17. 평가 결과 상세  - 한 사원의 단계별 타임라인 한 번에 조회
    //  - 목표등록 / 평가입력(자기·상위자) / 종합점수 / Z-score / 등급산정 / 보정이력 / 최종확정
    @GetMapping("/{gradeId}/detail")
    public ResponseEntity<EvalGradeDetailDto> getDetail(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long gradeId) {
        return ResponseEntity.ok(gradeService.getDetail(companyId, gradeId));
    }

    // 18. HR 전체 결과 조회 드롭다운 - 회사 전체 시즌 목록 (최신순, CLOSED 포함)
    @GetMapping("/seasons")
    public ResponseEntity<List<MySeasonOptionDto>> getAllSeasons(
            @RequestHeader("X-User-Company") UUID companyId) {
        return ResponseEntity.ok(gradeService.getAllSeasons(companyId));
    }


}
