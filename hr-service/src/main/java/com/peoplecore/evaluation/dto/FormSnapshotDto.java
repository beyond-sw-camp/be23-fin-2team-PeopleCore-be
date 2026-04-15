package com.peoplecore.evaluation.dto;



import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;


// 평가 규칙 JSON 스냅샷 역직렬화용
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FormSnapshotDto {
    private List<Item> itemList; //가중치설정 목록들 순회
    private List<Adjustment>adjustments; // 근태/징계 등 목록들 순회
    private List<GradeRule>gradeRules; //등급정의 (3~8)
    private List<RawScore>rawScoreTable; //팀장등급 ->원점수설정
    private KpiScoring kpiScoring; //kpi달성률 환산
    private Integer minTeamSize; //편향보정 소규모 팀 판정 기준 인원 (기본 5)


    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Item {
        private String id;              // 항목 식별자 (self/manager는 시스템 고정)
        private String name;            // 표시명
        private BigDecimal weight;      // 가중치 %
        private Boolean locked;         // true면 시스템 고정 항목 (자기평가/상위자평가) - 삭제/이름변경 불가
        private Boolean enabled;        // 고정 항목 사용 여부 (null/true면 사용, false면 점수 집계에서 제외)
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Adjustment {
        private String id;
        private String name;
        private BigDecimal points; //가감점수
        private Boolean enabled; //사용여부
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class GradeRule {
        private String id; //rawScoreTable/ManagerEval 연결 키
        private String label; //표시명(s,a)
        private BigDecimal ratio; // 강제배분%
        private String  color;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RawScore {
        private String gradeId; //GradeRule.id
        private BigDecimal rawScore; // 환산 원점수
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class KpiScoring {
        private BigDecimal cap; //달성률 상한
        private BigDecimal scaleTo; // 환산 만점
    }

}
