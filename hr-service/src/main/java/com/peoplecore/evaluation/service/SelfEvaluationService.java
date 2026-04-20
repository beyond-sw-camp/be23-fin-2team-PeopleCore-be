package com.peoplecore.evaluation.service;

import com.peoplecore.evaluation.domain.*;
import com.peoplecore.evaluation.dto.SelfEvaluationDraftRequest;
import com.peoplecore.evaluation.dto.SelfEvaluationResponse;
import com.peoplecore.evaluation.repository.GoalRepository;
import com.peoplecore.evaluation.repository.SeasonRepository;
import com.peoplecore.evaluation.repository.SelfEvaluationFileRepository;
import com.peoplecore.evaluation.repository.SelfEvaluationRepository;
import com.peoplecore.minio.service.MinioService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 자기평가 - 사원 실적 입력 및 제출
@Service
@Transactional
public class SelfEvaluationService {

    private final SelfEvaluationRepository selfEvaluationRepository;
    private final SelfEvaluationFileRepository selfEvaluationFileRepository;
    private final GoalRepository goalRepository;
    private final SeasonRepository seasonRepository;
    private final MinioService minioService;

    public SelfEvaluationService(SelfEvaluationRepository selfEvaluationRepository,
                                 SelfEvaluationFileRepository selfEvaluationFileRepository,
                                 GoalRepository goalRepository,
                                 SeasonRepository seasonRepository,
                                 MinioService minioService) {
        this.selfEvaluationRepository = selfEvaluationRepository;
        this.selfEvaluationFileRepository = selfEvaluationFileRepository;
        this.goalRepository = goalRepository;
        this.seasonRepository = seasonRepository;
        this.minioService = minioService;
    }

    // 1번 본인 자기평가 목록 - 회사 OPEN 시즌 내 승인된 목표 기준
    @Transactional(readOnly = true)
    public List<SelfEvaluationResponse> getMySelfEvaluations(UUID companyId, Long empId) {

        // 현재 OPEN 시즌
        Season openSeason = seasonRepository.findByCompany_CompanyIdAndStatus(companyId, EvalSeasonStatus.OPEN).orElse(null);
        if (openSeason == null) {
            throw new IllegalStateException("현재 진행 중인 시즌이 없습니다");
        }

        // 본인의 시즌 내 승인된 목표만 (자기평가 대상)
        List<Goal> goals = goalRepository.findByEmp_EmpIdAndSeason_SeasonIdAndApprovalStatusOrderByGoalIdDesc(empId, openSeason.getSeasonId(), GoalApprovalStatus.APPROVED);
        if (goals.isEmpty()) return new ArrayList<>();

        // Goal ID -> SelfEvaluation 매핑 (1query) // 목표의 자기평가 일괄조회
        List<Long> goalIds = new ArrayList<>();
        for (Goal g : goals) goalIds.add(g.getGoalId());

        List<SelfEvaluation> selfEvals = selfEvaluationRepository.findByGoal_GoalIdIn(goalIds);
        Map<Long, SelfEvaluation> selfByGoalId = new HashMap<>();
        for (SelfEvaluation s : selfEvals) {
            selfByGoalId.put(s.getGoal().getGoalId(), s);
        }

        // SelfEvaluation ID -> Files 매핑 (1query) // 자기평가의 파일 일괄 조회
        Map<Long, List<SelfEvaluationFile>> filesBySelfEvalId = new HashMap<>();
        if (!selfEvals.isEmpty()) {
            List<Long> selfEvalIds = new ArrayList<>();
            for (SelfEvaluation s : selfEvals) selfEvalIds.add(s.getSelfEvalId());

            List<SelfEvaluationFile> allFiles = selfEvaluationFileRepository.findBySelfEvaluation_SelfEvalIdIn(selfEvalIds);
            for (SelfEvaluationFile f : allFiles) {
                Long sid = f.getSelfEvaluation().getSelfEvalId();
                List<SelfEvaluationFile> list = filesBySelfEvalId.get(sid);
                if (list == null) {
                    list = new ArrayList<>();
                    filesBySelfEvalId.put(sid, list);
                }
                list.add(f);
            }
        }

        // 응답 조립 (목표 순서 유지)
        List<SelfEvaluationResponse> result = new ArrayList<>();
        for (Goal g : goals) {
            SelfEvaluation self = selfByGoalId.get(g.getGoalId());
            List<SelfEvaluationFile> files = self != null ? filesBySelfEvalId.get(self.getSelfEvalId()) : null;
            result.add(SelfEvaluationResponse.of(g, self, files));
        }
        return result;
    }

    // 2번 전체 임시저장 - submittedAt 유지, upsert (신규면 INSERT, 기존이면 필드만 교체)
    public List<SelfEvaluationResponse> saveDraft(UUID companyId, Long empId, SelfEvaluationDraftRequest request) {

        // 현재 OPEN 시즌
        Season openSeason = seasonRepository.findByCompany_CompanyIdAndStatus(companyId, EvalSeasonStatus.OPEN).orElse(null);
        if (openSeason == null) {
            throw new IllegalStateException("현재 진행 중인 시즌이 없습니다");
        }

        // 본인의 시즌 내 승인된 목표 전체
        List<Goal> goals = goalRepository.findByEmp_EmpIdAndSeason_SeasonIdAndApprovalStatusOrderByGoalIdDesc(empId, openSeason.getSeasonId(), GoalApprovalStatus.APPROVED);

        // goalId -> Goal 맵 (요청 goalId 가 본인 승인 목표인지 빠른 확인용)
        Map<Long, Goal> goalById = new HashMap<>();
        for (Goal g : goals) goalById.put(g.getGoalId(), g);

        // goalId -> 기존 SelfEvaluation 맵
        List<Long> goalIds = new ArrayList<>(goalById.keySet());
        List<SelfEvaluation> existing = goalIds.isEmpty() ? new ArrayList<>() : selfEvaluationRepository.findByGoal_GoalIdIn(goalIds);
        Map<Long, SelfEvaluation> selfByGoalId = new HashMap<>();
        for (SelfEvaluation s : existing) selfByGoalId.put(s.getGoal().getGoalId(), s);

        // 각 item 처리 - 신규 INSERT / 기존 UPDATE
        for (SelfEvaluationDraftRequest.Item item : request.getItems()) {
            Goal goal = goalById.get(item.getGoalId());
            if (goal == null) {
                throw new IllegalArgumentException("본인의 승인된 목표가 아닙니다");
            }

            SelfEvaluation self = selfByGoalId.get(item.getGoalId());
            if (self == null) {
                // 신규 생성 + 필드 주입 (기본 상태 = DRAFT)
                SelfEvaluation created = SelfEvaluation.builder()
                        .goal(goal)
                        .approvalStatus(SelfEvalApprovalStatus.DRAFT)
                        .build();
                created.updateDraft(item.getActualValue(), item.getAchievementLevel(),
                        item.getAchievementDetail(), item.getEvidence());
                selfEvaluationRepository.save(created);
            } else {
                // 작성중(DRAFT) 또는 반려(REJECTED) 만 수정 가능 (대기/승인은 금지)
                SelfEvalApprovalStatus st = self.getApprovalStatus();
                if (st != SelfEvalApprovalStatus.DRAFT && st != SelfEvalApprovalStatus.REJECTED) {
                    throw new IllegalStateException("제출된 자기평가는 수정할 수 없습니다");
                }
                // 반려 상태도 필드만 교체 (REJECTED/rejectReason 유지 - 제출 시점에만 초기화)
                self.updateDraft(item.getActualValue(), item.getAchievementLevel(),
                        item.getAchievementDetail(), item.getEvidence());
            }
        }
        // 최신 state 내려주기 위해 재조회
        return getMySelfEvaluations(companyId, empId);
    }


    // 3번 전체 제출 - 필수 필드 검증 + submittedAt=now 로 전환 (반려 흔적 초기화)
    public List<SelfEvaluationResponse> submitAll(UUID companyId, Long empId, SelfEvaluationDraftRequest request) {

        // 현재 OPEN 시즌
        Season openSeason = seasonRepository.findByCompany_CompanyIdAndStatus(companyId, EvalSeasonStatus.OPEN).orElse(null);
        if (openSeason == null) {
            throw new IllegalStateException("현재 진행 중인 시즌이 없습니다");
        }

        // 본인의 시즌 내 승인된 목표 전체
        List<Goal> goals = goalRepository.findByEmp_EmpIdAndSeason_SeasonIdAndApprovalStatusOrderByGoalIdDesc(empId, openSeason.getSeasonId(), GoalApprovalStatus.APPROVED);

        // goalId -> Goal 맵 (요청 goalId 가 본인 승인 목표인지 빠른 확인용)
        Map<Long, Goal> goalById = new HashMap<>();
        for (Goal g : goals) goalById.put(g.getGoalId(), g);

        // goalId -> 기존 SelfEvaluation 맵 (목표->자기평가 꺼냄)
        List<Long> goalIds = new ArrayList<>(goalById.keySet());
        List<SelfEvaluation> existing = goalIds.isEmpty() ? new ArrayList<>() : selfEvaluationRepository.findByGoal_GoalIdIn(goalIds);
        Map<Long, SelfEvaluation> selfByGoalId = new HashMap<>();
        for (SelfEvaluation s : existing) selfByGoalId.put(s.getGoal().getGoalId(), s);

        // 각 item 처리 - 자기평가 처음 작성=insert, 수정=update 후 submit()
        for (SelfEvaluationDraftRequest.Item item : request.getItems()) {
            Goal goal = goalById.get(item.getGoalId());
            if (goal == null) {
                throw new IllegalArgumentException("본인의 승인된 목표가 아닙니다");
            }

            // 제출 시 필수 필드 검증 (KPI = actualValue, OKR = achievementLevel, 공통 = achievementDetail)
            if (goal.getGoalType() == GoalType.KPI && item.getActualValue() == null) {
                throw new IllegalArgumentException("KPI 목표는 실적값이 필수입니다");
            }
            if (goal.getGoalType() == GoalType.OKR && item.getAchievementLevel() == null) {
                throw new IllegalArgumentException("OKR 목표는 달성수준이 필수입니다");
            }
            if (item.getAchievementDetail() == null || item.getAchievementDetail().isBlank()) {
                throw new IllegalArgumentException("달성 내용은 필수입니다");
            }

            SelfEvaluation self = selfByGoalId.get(item.getGoalId());
            if (self == null) {
                // 신규 생성 + 필드 주입 + 제출
                SelfEvaluation created = SelfEvaluation.builder()
                        .goal(goal)
                        .approvalStatus(SelfEvalApprovalStatus.DRAFT)
                        .build();
                created.updateDraft(item.getActualValue(), item.getAchievementLevel(),
                        item.getAchievementDetail(), item.getEvidence());
                created.submit();
                selfEvaluationRepository.save(created);
            } else {
                // 작성중(DRAFT) 또는 반려(REJECTED) 만 재제출 가능 (대기/승인은 금지)
                SelfEvalApprovalStatus st = self.getApprovalStatus();
                if (st != SelfEvalApprovalStatus.DRAFT && st != SelfEvalApprovalStatus.REJECTED) {
                    throw new IllegalStateException("제출된 자기평가는 수정할 수 없습니다");
                }
                // 필드 교체 + 제출 (submit() 이 PENDING 전환 + rejectReason 초기화)
                self.updateDraft(item.getActualValue(), item.getAchievementLevel(), item.getAchievementDetail(), item.getEvidence());
                self.submit();
            }
        }

        // 최신 state 내려주기 위해 재조회
        return getMySelfEvaluations(companyId, empId);
    }

    // 4번 근거 파일 업로드 - MinIO 업로드 후 메타 INSERT
    public SelfEvaluationResponse.FileResponse uploadFile(UUID companyId, Long empId, Long goalId, MultipartFile file) {

        // 현재 OPEN 시즌
        Season openSeason = seasonRepository.findByCompany_CompanyIdAndStatus(companyId, EvalSeasonStatus.OPEN).orElse(null);
        if (openSeason == null) {
            throw new IllegalStateException("현재 진행 중인 시즌이 없습니다");
        }

        // Goal 로드 + 본인의 승인된 목표인지 검증
        Goal goal = goalRepository.findById(goalId).orElse(null);
        if (goal == null) {
            throw new IllegalArgumentException("목표를 찾을 수 없습니다");
        }
        if (!goal.getEmp().getEmpId().equals(empId)) {
            throw new IllegalArgumentException("본인 목표만 파일을 올릴 수 있습니다");
        }
        if (!goal.getSeason().getSeasonId().equals(openSeason.getSeasonId())) {
            throw new IllegalStateException("현재 시즌의 목표가 아닙니다");
        }
        if (goal.getApprovalStatus() != GoalApprovalStatus.APPROVED) {
            throw new IllegalStateException("승인된 목표만 자기평가 파일을 올릴 수 있습니다");
        }

        // 해당 Goal 의 SelfEvaluation 확인 - 없으면 빈 row 자동 생성
        SelfEvaluation self = selfEvaluationRepository.findByGoal_GoalIdIn(List.of(goalId)).stream()
                .findFirst()
                .orElse(null);
        if (self == null) {
            self = SelfEvaluation.builder()
                    .goal(goal)
                    .approvalStatus(SelfEvalApprovalStatus.DRAFT)
                    .build();
            self = selfEvaluationRepository.save(self);
        } else {
            // 작성중(DRAFT) 또는 반려(REJECTED) 만 파일 추가 가능
            SelfEvalApprovalStatus st = self.getApprovalStatus();
            if (st != SelfEvalApprovalStatus.DRAFT && st != SelfEvalApprovalStatus.REJECTED) {
                throw new IllegalStateException("제출된 자기평가에는 파일을 추가할 수 없습니다");
            }
        }

        // MinIO 업로드
        String storedPath;
        try {
            storedPath = minioService.uploadFile(file, "self-evaluation");
        } catch (Exception e) {
            throw new IllegalStateException("파일 업로드에 실패했습니다", e);
        }

        // 파일 메타 INSERT
        SelfEvaluationFile saved = selfEvaluationFileRepository.save(SelfEvaluationFile.builder()
                .selfEvaluation(self)
                .originalFileName(file.getOriginalFilename())
                .storedFilePath(storedPath)
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .build());

        return SelfEvaluationResponse.FileResponse.from(saved);
    }


    // 5번 근거 파일 삭제 - DB row 삭제 후 MinIO 객체 제거 (MinIO 실패 시 throw -> 트랜잭션 롤백)
    public void deleteFile(UUID companyId, Long empId, Long goalId, Long fileId) {

        // 현재 OPEN 시즌
        Season openSeason = seasonRepository.findByCompany_CompanyIdAndStatus(companyId, EvalSeasonStatus.OPEN).orElse(null);
        if (openSeason == null) {
            throw new IllegalStateException("현재 진행 중인 시즌이 없습니다");
        }

        // 파일 로드
        SelfEvaluationFile fileEntity = selfEvaluationFileRepository.findById(fileId).orElse(null);
        if (fileEntity == null) {
            throw new IllegalArgumentException("파일을 찾을 수 없습니다");
        }

        SelfEvaluation self = fileEntity.getSelfEvaluation();
        Goal goal = self.getGoal();

        // URL 의 goalId 와 실제 파일 소속 goalId 일치 확인
        if (!goal.getGoalId().equals(goalId)) {
            throw new IllegalArgumentException("경로와 파일이 일치하지 않습니다");
        }
        // 본인 목표인지
        if (!goal.getEmp().getEmpId().equals(empId)) {
            throw new IllegalArgumentException("본인 목표의 파일만 삭제할 수 있습니다");
        }
        // 현재 시즌 목표인지
        if (!goal.getSeason().getSeasonId().equals(openSeason.getSeasonId())) {
            throw new IllegalStateException("현재 시즌의 파일만 삭제할 수 있습니다");
        }
        // 작성중(DRAFT) 또는 반려(REJECTED) 상태의 자기평가만 파일 삭제 가능
        SelfEvalApprovalStatus st = self.getApprovalStatus();
        if (st != SelfEvalApprovalStatus.DRAFT && st != SelfEvalApprovalStatus.REJECTED) {
            throw new IllegalStateException("제출된 자기평가의 파일은 삭제할 수 없습니다");
        }

        // DB row 삭제
        selfEvaluationFileRepository.delete(fileEntity);

        // MinIO 객체 삭제 (실패 시 throw -> 트랜잭션 롤백, 정합성 유지)
        try {
            minioService.deleteFile(fileEntity.getStoredFilePath());
        } catch (Exception e) {
            throw new IllegalStateException("파일 삭제에 실패했습니다", e);
        }
    }
}
