package com.peoplecore.grade.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "grade")
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Grade {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "grade_id")
    private Long gradeId;

    @Column(name = "grade_name", nullable = false, unique = true)
    private String gradeName;

    @Column(name = "grade_code", nullable = false, unique = true)
    private String gradeCode;

    @Column(name = "grade_order", nullable = false)
    private Integer gradeOrder;

    public void update(String gradeName, String gradeCode) {
        this.gradeName = gradeName;
        this.gradeCode = gradeCode;
    }

    public void updateOrder(Integer order) {
        this.gradeOrder = order;
    }
}
