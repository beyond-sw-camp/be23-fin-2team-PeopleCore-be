package com.peoplecore.evaluation.dto;

import com.peoplecore.department.domain.Department;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// 편향보정 실행/조회 결과 - 프론트 화면 표시용
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class BiasAdjustResultDto {
    private Long seasonId;                          // 조회 대상 시즌 ID
    private int processedCount;                     // 보정 처리된 인원 수 (0이면 미실행 → 프론트 배너 숨김)
    private List<TeamAnomalyDto> zeroStdDevTeams;   // 전원 동점(z-score 계산 불가) → 보정 스킵된 팀
    private List<TeamAnomalyDto> undersizedTeams;   // 소규모(팀원 부족) → 보정 스킵된 팀

    // 이상 팀 1건 - 배너 한 줄 단위 (static이어야 빌더가 outer 인스턴스 없이 생성 가능)
    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    public static class TeamAnomalyDto {
        private Long deptId;      // 부서 ID (상세 이동/식별용)
        private String deptName;  // 부서명 (사용자 표시용)


        // Department 엔티티 → DTO 변환 팩토리 //BiasAdjustResultDto에서만 쓰여너 innerclass내에 넣는다
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
}
