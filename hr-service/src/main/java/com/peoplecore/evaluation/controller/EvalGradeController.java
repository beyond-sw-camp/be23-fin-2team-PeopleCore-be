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


    // 4. 편향보정 이상 팀 조회 (GradeCalibration 화면 진입 시 호출)
    //  - DB에 저장된 EvalGrade.teamStdDev / teamSize 스냅샷을 읽어 이상 팀 복원
    //  -  단순 조회
    //  - 응답: processedCount(보정 처리 인원수) + zeroStdDevTeams(전원 동점 팀) + undersizedTeams(소규모 팀)
    //  - 미실행 시 processedCount=0, 리스트 빈 배열
    @GetMapping("/{seasonId}/bias-adjust/anomalies")
    public ResponseEntity<BiasAdjustResultDto> getBiasAdjustAnomalies(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long seasonId) {
        return ResponseEntity.ok(gradeService.getBiasAdjustAnomalies(companyId, seasonId));
    }


    // 5. 강제배분 적용-보정전 (수동 버튼+프론트에서 2,3호출 / 스케줄러 공통 /)
    //  - 시즌 전체 totalScore 내림차순 랭킹
    //  - 규칙의 ratio 대로 상위부터 autoGrade 부여 (마지막 등급은 잔여 할당)
    //  - 동점 시 비율산정>가감
    @PostMapping("/{seasonId}/distribution/apply")
    public ResponseEntity<Void> applyDistribution(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long seasonId) {
        gradeService.applyDistribution(companyId, seasonId);
        return ResponseEntity.noContent().build();
    }


    // ─── 등급 보정 페이지 ─────────────────────────

    // 6. 실제 vs 목표 분포 + 보정 건수
    //  - 페이지 상단 카드 5개 + "N개 등급 불일치" 배지 + "현재 보정 건수 N건"
    @GetMapping("/{seasonId}/distribution-diff")
    public ResponseEntity<DistributionDiffDto> getDistributionDiff(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long seasonId) {
        return ResponseEntity.ok(gradeService.getDistributionDiff(companyId, seasonId));
    }


    // 7. 보정 페이지 사원 목록
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


    // 8. 보정 이력
    //  autoGrade -> adjustedGrade
    @GetMapping("/{seasonId}/calibrations")
    public ResponseEntity<List<CalibrationHistoryDto>> getCalibrations(
            @RequestHeader("X-User-Company") UUID companyId,
            @PathVariable Long seasonId) {
        return ResponseEntity.ok(gradeService.getCalibrations(companyId, seasonId));
    }


    // 9. 일괄 보정 저장
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


//    // ─── 최종 등급 확정 및 잠금 페이지 ─────────────────
//
//    // 10. 확정 전 검증
//    //  - 응답: 미산정 직원 / 종합점수 누락 / 강제배분 비율 불일치 + 체크리스트 3종 + canFinalize
//    //  - 상단 배너 3종 + 우측 체크리스트를 한 번에 반환
//    @GetMapping("/{seasonId}/finalize/validation")
//    public ResponseEntity<FinalizeValidationDto> getFinalizeValidation(
//            @RequestHeader("X-User-Company") UUID companyId,
//            @PathVariable Long seasonId) {
//        return ResponseEntity.ok(gradeService.getFinalizeValidation(companyId, seasonId));
//    }
//
//
//    // 11. 강제배분 목표 vs 실제 비율 (확정 화면용)
//    //  - 등급별 목표%/실제%/실제인원/편차(+/-p) 반환
//    @GetMapping("/{seasonId}/finalize/distribution-compare")
//    public ResponseEntity<DistributionCompareDto> getFinalizeDistributionCompare(
//            @RequestHeader("X-User-Company") UUID companyId,
//            @PathVariable Long seasonId) {
//        return ResponseEntity.ok(gradeService.getFinalizeDistributionCompare(companyId, seasonId));
//    }
//
//
//    // 12. 최종 확정 및 잠금
//    //  - 검증 통과 시에만 실행. draftGrade → finalGrade 복사 + finalizedAt 기록
//    //  - 이후 수정 불가
//    @PostMapping("/{seasonId}/finalize")
//    public ResponseEntity<FinalizeResultDto> finalize(
//            @RequestHeader("X-User-Company") UUID companyId,
//            @RequestHeader("X-User-Emp") Long adjusterEmpId,
//            @PathVariable Long seasonId) {
//        return ResponseEntity.ok(gradeService.finalize(companyId, adjusterEmpId, seasonId));
//    }
//
//
//    // ─── 평가 결과 조회 페이지 ─────────────────────────
//
//    // 13. 최종 결과 목록 (시즌별)
//    //  - 응답: 사번/이름/부서/직급/종합점수/예정등급(autoGrade)/확정등급(finalGrade)/보정여부(calibrated)
//    //  - 필터: deptId, keyword(이름/사번)
//    //  - 정렬: Pageable sort
//    //  - 확정된 시즌(finalizedAt != null)만 조회 허용
//    @GetMapping("/{seasonId}/list/final")
//    public ResponseEntity<Page<FinalGradeListItemDto>> getFinalList(
//            @RequestHeader("X-User-Company") UUID companyId,
//            @PathVariable Long seasonId,
//            @RequestParam(required = false) Long deptId,
//            @RequestParam(required = false) String keyword,
//            @PageableDefault(size = 20) Pageable pageable) {
//        return ResponseEntity.ok(
//                gradeService.getFinalList(companyId, seasonId, deptId, keyword, pageable)
//        );
//    }
//
//
//    // 14. 평가 결과 상세 (HR 전용)
//    //  - 단계별 타임라인: 평가입력 -> 종합점수 -> Z-score보정 -> 등급산정 -> 보정 -> 최종확정
//    //  - 각 단계 status: DONE | PENDING | SKIPPED
//    //  - 시즌 확정 전에도 조회 가능 (미완료 단계는 data=null, PENDING)
//    //  - 보정 없이 확정된 경우 5단계 SKIPPED
//    @GetMapping("/{evalGradeId}/detail")
//    public ResponseEntity<EvalGradeDetailDto> getDetail(
//            @RequestHeader("X-User-Company") UUID companyId,
//            @PathVariable Long evalGradeId) {
//        return ResponseEntity.ok(gradeService.getDetail(companyId, evalGradeId));
//    }

}
