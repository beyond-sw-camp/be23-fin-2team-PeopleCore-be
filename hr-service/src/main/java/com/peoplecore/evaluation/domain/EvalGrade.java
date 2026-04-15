package com.peoplecore.evaluation.domain;

import com.peoplecore.employee.domain.Employee;
import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// 등급 - 사원별 시즌 최종 등급 (자동산정 + 보정)
@Entity
@Table(name = "grade")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvalGrade extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "grade_id")
    private Long gradeId; // 등급 PK

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emp_id")
    private Employee emp; // 대상 사원

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id")
    private Season season; // 시즌

    @Column(name = "total_score", precision = 6, scale = 2)
    private BigDecimal totalScore; // 총점 (원점수)

    @Column(name = "weighted_score", precision = 6, scale = 2)
    private BigDecimal weightedScore; // 자기+팀장 가중평균 (비율)

    @Column(name = "adjustment_score", precision = 6, scale = 2)
    private BigDecimal adjustmentScore; // 가감점수

    @Column(name = "bias_adjusted_score", precision = 6, scale = 2)
    private BigDecimal biasAdjustedScore; // Z-score 편향보정 후 점수 (기본값=totalScore)

    @Column(name = "rank_in_season")
    private Integer rankInSeason; // 시즌 전체 순위 (3번 강제배분 결과)

    @Column(name = "auto_grade", length = 5)
    private String autoGrade; // 자동 등급 (보정 전)

    @Column(name = "final_grade", length = 5)
    private String finalGrade; // 최종 등급 (보정 후)

    @Column(name = "is_calibrated")
    @Builder.Default
    private Boolean isCalibrated = false; // 보정 여부

    @Column(name = "locked_at")
    private LocalDateTime lockedAt; // 최종확정 시각

    // ─── 감사용 스냅샷 (결과 조회 상세 화면 근거) ───

    @Column(name = "team_avg", precision = 6, scale = 2)
    private BigDecimal teamAvg; // 팀 평균 (Z-score 계산 근거)

    @Column(name = "team_std_dev", precision = 6, scale = 2)
    private BigDecimal teamStdDev; // 팀 표준편차

    @Column(name = "company_avg", precision = 6, scale = 2)
    private BigDecimal companyAvg; // 전사 평균

    @Column(name = "company_std_dev", precision = 6, scale = 2)
    private BigDecimal companyStdDev; // 전사 표준편차

    @Column(name = "rank_in_team")
    private Integer rankInTeam; // 팀 내 순위

    @Column(name = "team_size")
    private Integer teamSize; // 당시 팀 인원 (분모)

    @Column(name = "dept_id_snapshot")
    private Long deptIdSnapshot; // 시즌 시작 시점 부서 ID

    @Column(name = "dept_name_snapshot", length = 50)
    private String deptNameSnapshot; // 시즌 시작 시점 부서명

    @Column(name = "position_snapshot", length = 20)
    private String positionSnapshot; // 시즌 시작 시점 직급


    

//    강제배분 결과 -autoGrade + 순위 저장(보정전)
    public void applyDistribution(String autoGrade, Integer rankInSeason){
        this.autoGrade = autoGrade;
        this.rankInSeason = rankInSeason;
    }

//    자동산정 결과 - 비율/가감/종합 분리 저장
    public void applyTotalScore(BigDecimal weighted, BigDecimal adjustment, BigDecimal total){
        this.weightedScore = weighted;
        this.adjustmentScore = adjustment;
        this.totalScore = total;
    }

//    zscore편향보정 결과+통계 스냅샷 저장
    public void applyBiasAdjustment(BigDecimal biasAdjustedScore, //z-score보정 후 최종 점수
                                    BigDecimal teamAvg,
                                    BigDecimal teamStDev, //팀 점수의 표준편차
                                    BigDecimal companyAvg,
                                    BigDecimal companyStdDev,//전사 표준편차(리스케일 스타일)
                                    Integer teamSize){
//        보정된 점수 저장
        this.biasAdjustedScore = biasAdjustedScore;
        this.teamAvg = teamAvg; //팀평균 -> 팀내의 본인 위치
        this.teamStdDev = teamStDev; //팀편차
        this.companyAvg = companyAvg; //전사평균 -> 전체 기준선
        this.companyStdDev = companyStdDev; //전사편차 -> 전체 분포 스케일
        this.teamSize = teamSize; //팀 인원수

    }

}
