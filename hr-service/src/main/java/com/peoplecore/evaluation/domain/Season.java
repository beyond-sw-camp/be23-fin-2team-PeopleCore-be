package com.peoplecore.evaluation.domain;

import com.peoplecore.company.domain.Company;
import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

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

    @Column(name = "name", nullable = false, length = 100)
    private String name; // 시즌명

    @Column(name = "period", length = 20)
    private String period; // 기간구분 (상반기/하반기/연간)

    @Column(name = "start_date")
    private LocalDate startDate; // 시작일

    @Column(name = "end_date")
    private LocalDate endDate; // 종료일

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private EvalSeasonStatus status; // 시즌 상태

    public void updateBasicInfo(String name, String period, LocalDate startDate, LocalDate endDate){
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
}
