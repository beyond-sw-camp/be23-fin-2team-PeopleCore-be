package com.peoplecore.evaluation.domain;

import com.peoplecore.entity.BaseTimeEntity;
import com.peoplecore.grade.domain.Grade;
import com.peoplecore.title.domain.Title;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

// 회사당 1행. grade 또는 title 중 정확히 하나만 set. FK 로 dangling ref 차단.
@Entity
@Table(
    name = "evaluator_role_config",
    uniqueConstraints = @UniqueConstraint(name = "uk_evaluator_role_company", columnNames = "company_id")
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvaluatorRoleConfig extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "config_id")
    private Long id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    // FK to grade. nullable — title 쪽 set 이면 null.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "grade_id",
        foreignKey = @ForeignKey(name = "fk_evaluator_role_grade")
    )
    private Grade grade;

    // FK to title. nullable — grade 쪽 set 이면 null.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "title_id",
        foreignKey = @ForeignKey(name = "fk_evaluator_role_title")
    )
    private Title title;

    // 저장/수정 직전 XOR 검증. 둘 다 null / 둘 다 not null 이면 에러.
    @PrePersist
    @PreUpdate
    private void checkXor() {
        if ((grade == null) == (title == null)) {
            throw new IllegalStateException(
                "evaluator_role_config: grade_id 와 title_id 중 정확히 하나만 지정돼야 합니다.");
        }
    }

    // 직급 쪽으로 지정 (직책은 자동 해제)
    public void setGradeTarget(Grade newGrade) {
        this.grade = newGrade;
        this.title = null;
    }

    // 직책 쪽으로 지정 (직급은 자동 해제)
    public void setTitleTarget(Title newTitle) {
        this.title = newTitle;
        this.grade = null;
    }

    // DTO 변환용: 어느 축으로 지정됐는지
    public EvaluatorRoleMode deriveMode() {
        return grade != null ? EvaluatorRoleMode.GRADE : EvaluatorRoleMode.TITLE;
    }

    // DTO 변환용: 지정된 id (grade_id 또는 title_id)
    public Long getGrantedTargetId() {
        if (grade != null) return grade.getGradeId();
        if (title != null) return title.getTitleId();
        return null;
    }
}
