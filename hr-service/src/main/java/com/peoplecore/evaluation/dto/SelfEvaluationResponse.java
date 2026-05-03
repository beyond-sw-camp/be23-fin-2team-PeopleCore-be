package com.peoplecore.evaluation.dto;

import com.peoplecore.evaluation.domain.AchievementLevel;
import com.peoplecore.evaluation.domain.Goal;
import com.peoplecore.evaluation.domain.GoalType;
import com.peoplecore.evaluation.domain.SelfEvalApprovalStatus;
import com.peoplecore.evaluation.domain.SelfEvaluation;
import com.peoplecore.evaluation.domain.SelfEvaluationFile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// 자기평가 1건 응답 - 목표 정보 + 자기평가 필드 + 첨부 파일
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SelfEvaluationResponse {

    // 목표 정보
    private Long goalId;
    private GoalType goalType;                 // KPI / OKR
    private String category;
    private String title;
    private String description;
    private Integer weight;                    // 가중치(%) - KPI 만 값, OKR 은 null
    private Long kpiTemplateId;
    private BigDecimal targetValue;
    private String targetUnit;

    // 자기평가 (아직 작성 안 한 경우 selfEvalId 포함 전부 null, approval 은 DRAFT)
    private Long selfEvalId;
    private BigDecimal actualValue;
    private AchievementLevel achievementLevel; // OKR 달성수준
    private String achievementDetail;
    private String evidence;
    private SelfEvalApprovalStatus approval;   // DRAFT / PENDING / APPROVED / REJECTED
    private String rejectReason;
    private LocalDateTime submittedAt;         // 제출 이력 (null = 미제출)

    // 첨부 파일
    private List<FileResponse> files;

    // Entity -> DTO (자기평가 미작성 시 self 필드는 null, files 는 빈 리스트, approval 은 DRAFT)
    public static SelfEvaluationResponse of(Goal g, SelfEvaluation s, List<SelfEvaluationFile> fileEntities) {
        List<FileResponse> fileDtos = new ArrayList<>();
        if (fileEntities != null) {
            for (SelfEvaluationFile f : fileEntities) {
                fileDtos.add(FileResponse.from(f));
            }
        }

        SelfEvaluationResponseBuilder b = SelfEvaluationResponse.builder()
                .goalId(g.getGoalId())
                .goalType(g.getGoalType())
                .category(g.getCategory())
                .title(g.getTitle())
                .description(g.getDescription())
                .weight(g.getWeight())
                .kpiTemplateId(g.getKpiTemplate() != null ? g.getKpiTemplate().getKpiId() : null)
                .targetValue(g.getTargetValue())
                .targetUnit(g.getTargetUnit())
                .files(fileDtos);

        if (s != null) {
            b.selfEvalId(s.getSelfEvalId())
                    .actualValue(s.getActualValue())
                    .achievementLevel(s.getAchievementLevel())
                    .achievementDetail(s.getAchievementDetail())
                    .evidence(s.getEvidence())
                    .approval(s.getApprovalStatus())
                    .rejectReason(s.getRejectReason())
                    .submittedAt(s.getSubmittedAt());
        } else {
            b.approval(SelfEvalApprovalStatus.DRAFT);
        }
        return b.build();
    }

    // 근거 파일 1건 (storedFilePath 는 서버 내부 정보라 제외)
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FileResponse {
        private Long fileId;
        private String originalFileName;
        private String contentType;
        private Long fileSize;

        public static FileResponse from(SelfEvaluationFile f) {
            return FileResponse.builder()
                    .fileId(f.getFileId())
                    .originalFileName(f.getOriginalFileName())
                    .contentType(f.getContentType())
                    .fileSize(f.getFileSize())
                    .build();
        }
    }
}
