package com.peoplecore.evaluation.domain;

import com.peoplecore.company.domain.Company;
import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 평가시즌 - 평가 전체 기간 단위
@Entity
@Table(name = "season")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Season extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "season_id")
    private Long seasonId; // 시즌 PK

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company; // 소속 회사

    @Column(name = "jobTypeName", nullable = false, length = 100)
    private String name; // 시즌명

    @Enumerated(EnumType.STRING)
    @Column(name = "period", length = 20)
    private SeasonPeriod period; // 기간구분 (상반기/하반기/연간)

    @Column(name = "start_date")
    private LocalDate startDate; // 시작일

    @Column(name = "end_date")
    private LocalDate endDate; // 종료일

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private EvalSeasonStatus status; // 시즌 상태

    @Column(name = "finalized_at")
    private LocalDateTime finalizedAt; // 최종 등급 확정 시각 (이후 수정 불가)

    // 시즌 OPEN 시점에 회사 규칙(EvaluationRules.formValues)을 통째로 복사해 박제
    // 이후 평가 산정/보정/분포 계산은 모두 이 스냅샷 기준
    @Column(name = "form_snapshot", columnDefinition = "JSON")
    private String formSnapshot;

    // 스냅샷 찍을 때의 회사 규칙 버전 (감사 추적용)
    @Column(name = "form_version")
    private Long formVersion;

    public void updateBasicInfo(String name, SeasonPeriod period, LocalDate startDate, LocalDate endDate){
        this.name = name;
        this.period = period;
        this.startDate = startDate;
        this.endDate =endDate;
    }

//    스케줄러 호출 DRAFT → OPEN
    public void open(){
        this.status = EvalSeasonStatus.OPEN;
    }
//    OPEN → CLOSED
    public void close(){
        this.status = EvalSeasonStatus.CLOSED;
    }

//    시즌 OPEN 시점에 회사 규칙 JSON + 버전을 박제
    public void freezeSnapshot(String formJson, Long version){
        this.formSnapshot = formJson;
        this.formVersion = version;
    }

//    최종 확정 - finalizedAt세팅( 이후 수정 불가)
    public void markFinalized(LocalDateTime now){
        this.finalizedAt = now;
    }
}
