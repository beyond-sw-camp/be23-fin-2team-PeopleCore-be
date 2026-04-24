package com.peoplecore.evaluation.service;

import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.evaluation.domain.*;
import com.peoplecore.evaluation.dto.SelfEvaluationDraftRequest;
import com.peoplecore.evaluation.dto.SelfEvaluationResponse;
import com.peoplecore.evaluation.dto.TeamMemberSelfEvaluationResponse;
import com.peoplecore.evaluation.repository.GoalRepository;
import com.peoplecore.evaluation.repository.SeasonRepository;
import com.peoplecore.evaluation.repository.SelfEvaluationFileRepository;
import com.peoplecore.evaluation.repository.SelfEvaluationRepository;
import com.peoplecore.minio.service.MinioService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
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
    private final EmployeeRepository employeeRepository;
    private final MinioService minioService;

    public SelfEvaluationService(SelfEvaluationRepository selfEvaluationRepository,
                                 SelfEvaluationFileRepository selfEvaluationFileRepository,
                                 GoalRepository goalRepository,
                                 SeasonRepository seasonRepository,
                                 EmployeeRepository employeeRepository,
                                 MinioService minioService) {
        this.selfEvaluationRepository = selfEvaluationRepository;
        this.selfEvaluationFileRepository = selfEvaluationFileRepository;
        this.goalRepository = goalRepository;
        this.seasonRepository = seasonRepository;
        this.employeeRepository = employeeRepository;
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

//  팀장 달성도 검토

    // 6번 팀원 자기평가 목록 - 팀원별 묶음 (상단 카드 집계 -> 프론트)
    @Transactional(readOnly = true)
    public List<TeamMemberSelfEvaluationResponse> getTeamSelfEvaluations(UUID companyId, Long managerId) {

        // 현재 팀장이 볼 수 있는 팀원 목록 (임시 정책: 같은 부서 사원)
        List<Employee> teamMembers = findTeamMembers(companyId, managerId);
        if (teamMembers.isEmpty()) return new ArrayList<>();

        // 현재 진행 중인 시즌
        Season openSeason = seasonRepository.findByCompany_CompanyIdAndStatus(companyId, EvalSeasonStatus.OPEN).orElse(null);
        if (openSeason == null) {
            throw new IllegalStateException("현재 진행 중인 시즌이 없습니다");
        }

        // IN 쿼리용으로 팀원 empId 만 뽑아둠
        List<Long> teamEmpIds = new ArrayList<>();
        for (Employee e : teamMembers) teamEmpIds.add(e.getEmpId());

        // 팀원 전체 목표 한 번에 조회 -> 팀장이 승인한 것만 (자기평가 대상)
        List<Goal> allGoals = goalRepository.findByEmp_EmpIdInAndSeason_SeasonIdOrderByGoalIdDesc(
                teamEmpIds, openSeason.getSeasonId());
        List<Goal> approvedGoals = new ArrayList<>();
        for (Goal g : allGoals) {
            if (g.getApprovalStatus() == GoalApprovalStatus.APPROVED) approvedGoals.add(g);
        }

        // 해당 목표들에 딸린 자기평가를 한 번에 가져와서 goalId 로 바로 찾게 Map 구성
        List<Long> goalIds = new ArrayList<>();
        for (Goal g : approvedGoals) goalIds.add(g.getGoalId());
        Map<Long, SelfEvaluation> selfByGoalId = new HashMap<>();
        if (!goalIds.isEmpty()) {
            for (SelfEvaluation s : selfEvaluationRepository.findByGoal_GoalIdIn(goalIds)) {
                selfByGoalId.put(s.getGoal().getGoalId(), s);
            }
        }

        // 자기평가들에 딸린 파일을 한 번에 조회해서 selfEvalId 별로 묶기 (한 자기평가에 파일 N개 가능)
        Map<Long, List<SelfEvaluationFile>> filesBySelfEvalId = new HashMap<>();
        if (!selfByGoalId.isEmpty()) {
            List<Long> selfEvalIds = new ArrayList<>();
            for (SelfEvaluation s : selfByGoalId.values()) selfEvalIds.add(s.getSelfEvalId());

            for (SelfEvaluationFile f : selfEvaluationFileRepository.findBySelfEvaluation_SelfEvalIdIn(selfEvalIds)) {
                Long sid = f.getSelfEvaluation().getSelfEvalId();
                List<SelfEvaluationFile> list = filesBySelfEvalId.get(sid);
                if (list == null) {
                    list = new ArrayList<>();
                    filesBySelfEvalId.put(sid, list);
                }
                list.add(f);
            }
        }

        // 팀원별 목표 묶기 - 응답 조립할 때 사원 하나하나 돌면서 그 사원 목표를 O(1) 로 꺼내기
        Map<Long, List<Goal>> goalsByEmpId = new HashMap<>();
        for (Goal g : approvedGoals) {
            Long eid = g.getEmp().getEmpId();
            List<Goal> list = goalsByEmpId.get(eid);
            if (list == null) {
                list = new ArrayList<>();
                goalsByEmpId.put(eid, list);
            }
            list.add(g);
        }

        // 팀원 순서 유지하며 응답 조립 - 사원정보 + 각 목표의 자기평가(+파일) + 가장 최근 제출일
        List<TeamMemberSelfEvaluationResponse> result = new ArrayList<>();
        for (Employee emp : teamMembers) {
            List<Goal> memberGoals = goalsByEmpId.get(emp.getEmpId());
            if (memberGoals == null) memberGoals = new ArrayList<>();

            LocalDateTime latestSubmitted = null;
            List<SelfEvaluationResponse> evalDtos = new ArrayList<>();
            for (Goal g : memberGoals) {
                SelfEvaluation self = selfByGoalId.get(g.getGoalId());
                // 자기평가 미작성(null) 또는 임시저장(DRAFT) 은 평가자 화면에 노출하지 않음
                //   -> 정식 제출(PENDING/APPROVED/REJECTED) 된 것만 달성도 검토 대상
                if (self == null) continue;
                if (self.getApprovalStatus() == SelfEvalApprovalStatus.DRAFT) continue;

                List<SelfEvaluationFile> files = filesBySelfEvalId.get(self.getSelfEvalId());
                evalDtos.add(SelfEvaluationResponse.of(g, self, files));
                // 가장 늦은 제출 시각 갱신 (프론트 "제출일" 표시용)
                if (self.getSubmittedAt() != null
                        && (latestSubmitted == null || self.getSubmittedAt().isAfter(latestSubmitted))) {
                    latestSubmitted = self.getSubmittedAt();
                }
            }

            result.add(TeamMemberSelfEvaluationResponse.builder()
                    .id(emp.getEmpId())
                    .employeeName(emp.getEmpName())
                    .dept(emp.getDept().getDeptName())
                    .position(emp.getGrade() != null ? emp.getGrade().getGradeName() : null)
                    .submittedDate(latestSubmitted)
                    .evaluations(evalDtos)
                    .build());
        }
        return result;
    }

    // 7번 근거 파일 다운로드 - MinIO 스트리밍 (팀장이 팀원 파일 열람)
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> downloadFile(UUID companyId, Long managerId, Long goalId, Long fileId) {

        // 파일 메타 로드
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

        // 팀장이 이 사원을 관리하는지 (팀원 범위 확인)
        validateManagerAccess(companyId, managerId, goal.getEmp().getEmpId());

        // MinIO 에서 InputStream 가져오기
        InputStream in;
        try {
            in = minioService.downloadFile(fileEntity.getStoredFilePath());
        } catch (Exception e) {
            throw new IllegalStateException("파일 다운로드에 실패했습니다", e);
        }

        // 한글 파일명을 위해 RFC 5987 인코딩 적용
        String encodedName = URLEncoder.encode(fileEntity.getOriginalFileName(), StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");
        String contentType = fileEntity.getContentType() != null
                ? fileEntity.getContentType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(fileEntity.getFileSize())
                .body(new InputStreamResource(in));
    }

    // 8번 단건 승인 - PENDING 상태만 가능 (DRAFT/APPROVED/REJECTED 은 거부)
    public SelfEvaluationResponse approveSelfEvaluation(UUID companyId, Long managerId, Long goalId) {
        SelfEvaluation self = loadSelfEvalForManager(companyId, managerId, goalId);

        if (self.getApprovalStatus() != SelfEvalApprovalStatus.PENDING) {
            throw new IllegalStateException("대기 상태가 아닌 자기평가는 승인할 수 없습니다");
        }

        self.approve();

        // 승인 직후 상태 + 파일 목록 담아 응답 (승인처리한 이력(승인완료))
        List<SelfEvaluationFile> files = selfEvaluationFileRepository.findBySelfEvaluation_SelfEvalIdOrderByFileIdAsc(self.getSelfEvalId());
        return SelfEvaluationResponse.of(self.getGoal(), self, files);
    }



    // 9번 단건 반려 - PENDING 상태만 가능, 사유 필수
    public SelfEvaluationResponse rejectSelfEvaluation(UUID companyId, Long managerId, Long goalId, String rejectReason) {
        if (rejectReason == null || rejectReason.isBlank()) {
            throw new IllegalArgumentException("반려 사유는 필수입니다");
        }

        SelfEvaluation self = loadSelfEvalForManager(companyId, managerId, goalId);

        if (self.getApprovalStatus() != SelfEvalApprovalStatus.PENDING) {
            throw new IllegalStateException("대기 상태가 아닌 자기평가는 반려할 수 없습니다");
        }

        self.reject(rejectReason);

        // 반려 직후 상태 + 파일 목록 담아 응답 (프론트 카드 갱신용)
        List<SelfEvaluationFile> files = selfEvaluationFileRepository.findBySelfEvaluation_SelfEvalIdOrderByFileIdAsc(self.getSelfEvalId());
        return SelfEvaluationResponse.of(self.getGoal(), self, files);
    }



    // 10번 팀원 단위 일괄 승인 - 해당 팀원의 대기(PENDING) 자기평가 전부 승인
    public List<SelfEvaluationResponse> approveAllPendingSelfEvaluations(UUID companyId, Long managerId, Long targetEmpId) {

        // 팀원 소속 확인
        validateManagerAccess(companyId, managerId, targetEmpId);

        // 현재 OPEN 시즌
        Season openSeason = seasonRepository.findByCompany_CompanyIdAndStatus(companyId, EvalSeasonStatus.OPEN).orElse(null);
        if (openSeason == null) {
            throw new IllegalStateException("현재 진행 중인 시즌이 없습니다");
        }

        // 해당 팀원의 시즌 내 승인된 목표만 (자기평가 대상)
        List<Goal> goals = goalRepository.findByEmp_EmpIdAndSeason_SeasonIdAndApprovalStatusOrderByGoalIdDesc(targetEmpId, openSeason.getSeasonId(), GoalApprovalStatus.APPROVED);
        if (goals.isEmpty()) return new ArrayList<>();

        // 목표들의 자기평가 일괄 조회
        List<Long> goalIds = new ArrayList<>();
        for (Goal g : goals) goalIds.add(g.getGoalId());
        List<SelfEvaluation> selfEvals = selfEvaluationRepository.findByGoal_GoalIdIn(goalIds);

        // PENDING 만 골라 승인 (방금 승인된 것만 응답)
        List<SelfEvaluation> approved = new ArrayList<>();
        for (SelfEvaluation s : selfEvals) {
            if (s.getApprovalStatus() == SelfEvalApprovalStatus.PENDING) {
                s.approve();
                approved.add(s);
            }
        }
        if (approved.isEmpty()) return new ArrayList<>();

        // 승인된 자기평가들의 파일 일괄 조회 후 selfEvalId 별 묶음
        List<Long> selfEvalIds = new ArrayList<>();
        for (SelfEvaluation s : approved) selfEvalIds.add(s.getSelfEvalId());
        Map<Long, List<SelfEvaluationFile>> filesBySelfEvalId = new HashMap<>();
        for (SelfEvaluationFile f : selfEvaluationFileRepository.findBySelfEvaluation_SelfEvalIdIn(selfEvalIds)) {
            Long sid = f.getSelfEvaluation().getSelfEvalId();
            List<SelfEvaluationFile> list = filesBySelfEvalId.get(sid);
            if (list == null) {
                list = new ArrayList<>();
                filesBySelfEvalId.put(sid, list);
            }
            list.add(f);
        }

        // 응답 조립 (방금 승인한 자기평가만 반환)
        List<SelfEvaluationResponse> result = new ArrayList<>();
        for (SelfEvaluation s : approved) {
            List<SelfEvaluationFile> files = filesBySelfEvalId.get(s.getSelfEvalId());
            result.add(SelfEvaluationResponse.of(s.getGoal(), s, files));
        }
        return result;
    }



    // 팀장 자기평가 단건 처리 공통  - goalId 로 SelfEval 로드 + 팀원 소속 확인 (8번/9번)
    private SelfEvaluation loadSelfEvalForManager(UUID companyId, Long managerId, Long goalId) {
        List<SelfEvaluation> list = selfEvaluationRepository.findByGoal_GoalIdIn(List.of(goalId));
        if (list.isEmpty()) {
            throw new IllegalArgumentException("자기평가를 찾을 수 없습니다");
        }
        SelfEvaluation self = list.get(0);
        validateManagerAccess(companyId, managerId, self.getGoal().getEmp().getEmpId());
        return self;
    }

    // 팀장이 해당 사원을 관리하는 팀원 범위에 있는지 확인
    private void validateManagerAccess(UUID companyId, Long managerId, Long targetEmpId) {
        List<Employee> members = findTeamMembers(companyId, managerId);
        for (Employee m : members) {
            if (m.getEmpId().equals(targetEmpId)) return;
        }
        throw new IllegalArgumentException("본인 팀원이 아닙니다");
    }

    // 팀장 기준 팀원 범위 결정 (임시 정책: 같은 부서 사원 = 팀원, 본인 제외)
    // TODO: Title 기반 MANAGER role 확정되면 본체만 교체
    private List<Employee> findTeamMembers(UUID companyId, Long managerId) {
        Employee manager = employeeRepository.findById(managerId).orElse(null);
        if (manager == null || !manager.getCompany().getCompanyId().equals(companyId)) {
            throw new IllegalArgumentException("팀장 정보가 없습니다");
        }
        List<Employee> all = employeeRepository.findActiveByCompanyAndDept(companyId, manager.getDept().getDeptId());
        List<Employee> members = new ArrayList<>();
        for (Employee e : all) {
            if (!e.getEmpId().equals(managerId)) members.add(e);
        }
        return members;
    }
}
