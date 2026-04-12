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
    private BigDecimal totalScore; // 총점

    @Column(name = "auto_grade", length = 5)
    private String autoGrade; // 자동 등급 (보정 전)

    @Column(name = "final_grade", length = 5)
    private String finalGrade; // 최종 등급 (보정 후)

    @Column(name = "is_calibrated")
    @Builder.Default
    private Boolean isCalibrated = false; // 보정 여부

    @Column(name = "locked_at")
    private LocalDateTime lockedAt; // 최종확정 시각
}
