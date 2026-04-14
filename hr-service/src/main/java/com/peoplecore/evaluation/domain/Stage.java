package com.peoplecore.evaluation.domain;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

// 단계 - 시즌 내 진행 단계 (목표등록/자기평가/상위자평가/등급산정/결과확정)
@Entity
@Table(name = "stage")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Stage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stage_id")
    private Long stageId; // 단계 PK

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id", nullable = false)
    private Season season; // 소속 시즌

    @Column(name = "name", length = 50)
    private String name; // 단계명

    @Column(name = "order_no")
    private Integer orderNo; // 순서

    @Column(name = "start_date")
    private LocalDate startDate; // 시작일

    @Column(name = "end_date")
    private LocalDate endDate; // 종료일

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    @Builder.Default
    private StageStatus status = StageStatus.WAITING; // 상태 (대기/진행중/마감)

//    스케줄러 호출 — 상태 전이
    public void start()  { this.status = StageStatus.IN_PROGRESS; }
    public void finish() { this.status = StageStatus.FINISHED; }
}
