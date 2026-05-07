package com.peoplecore.evaluation.domain;

import com.peoplecore.employee.domain.Employee;
import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

// 보정이력 - 등급 조정 이력
@Entity
@Table(name = "calibration")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Calibration extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "calibration_id")
    private Long calibrationId; // 보정이력 PK

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grade_id")
    private EvalGrade grade; // 대상 등급

    @Column(name = "from_grade", length = 5)
    private String fromGrade; // 변경 전 등급

    @Column(name = "to_grade", length = 5)
    private String toGrade; // 변경 후 등급

    @Column(name = "reason", length = 1000)
    private String reason; // 보정 사유

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private Employee actor; // 보정 수행자 (HR)
}
