package com.peoplecore.evaluation.dto;

import com.peoplecore.department.domain.Department;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

// 등급 보정(calibration) 검토 대상 정보 - 프론트 화면 배너/섹션 렌더링용
//   - 편향보정 스킵된 팀 (전원 동점 / 소규모) - Z-score 보정 불가 팀
//   - 자기평가 scaleTo 초과로 clip 된 사원 - 원래 점수 더 높았으나 상한에 잘림 → 등급 상향 후보
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class CalibrationReviewDto {
    private Long seasonId;                                    // 조회 대상 시즌 ID
    private int processedCount;                               // 편향보정 처리된 인원 수 (0이면 미실행 → 프론트 배너 숨김)
    private List<TeamAnomalyDto> zeroStdDevTeams;             // 전원 동점(z-score 계산 불가) → 보정 스킵된 팀
    private List<TeamAnomalyDto> undersizedTeams;             // 소규모(팀원 부족) → 보정 스킵된 팀
    private List<ClippedSelfEmployeeDto> clippedSelfEmployees; // 자기평가 scaleTo 초과로 잘린 사원 (등급 보정 후보)

    // 이상 팀 1건 - 배너 한 줄 단위 (static이어야 빌더가 outer 인스턴스 없이 생성 가능)
    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    public static class TeamAnomalyDto {
        private Long deptId;      // 부서 ID (상세 이동/식별용)
        private String deptName;  // 부서명 (사용자 표시용)


        // Department 엔티티 → DTO 변환 팩토리 //CalibrationReviewDto에서만 쓰여서 innerclass내에 넣는다
        public static TeamAnomalyDto from(Department dept) {
            return TeamAnomalyDto.builder()
                    .deptId(dept.getDeptId())       // 엔티티의 부서 ID 복사
                    .deptName(dept.getDeptName())   // 엔티티의 부서명 복사
                    .build();
        }

        // 부서 조회 실패 시 폴백 - ID만 알고 이름 모를 때 UI 깨짐 방지
        public static TeamAnomalyDto ofMissing(Long deptId) {
            return TeamAnomalyDto.builder()
                    .deptId(deptId)
                    .deptName("dept#" + deptId)     // 임시 표시 이름
                    .build();
        }
    }

    // 자기평가 clip 대상자 1건 - HR 이 등급 상향 고려용으로 검토
    //   - rawSelfScore > scaleTo 인 사원이 표시됨
    //   - UI 에 "원래 점수 X, 상한 적용 후 Y" 노출해 상향 판단 근거 제공
    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    public static class ClippedSelfEmployeeDto {
        private Long empId;             // 사원 ID
        private String empName;         // 사원명
        private String deptName;        // 부서명 (시즌 박제값)
        private BigDecimal rawSelfScore; // clip 전 원 점수 (scaleTo 초과분 포함)
        private BigDecimal selfScore;    // clip 후 점수 (=scaleTo)
        private String autoGrade;        // 현재 자동 등급 (참고용, 없으면 null)
    }
}
