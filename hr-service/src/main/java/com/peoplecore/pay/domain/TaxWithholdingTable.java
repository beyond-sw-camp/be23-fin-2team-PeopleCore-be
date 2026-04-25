package com.peoplecore.pay.domain;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@Table(name = "tax_withholding_table",  //간이세액표
        uniqueConstraints = @UniqueConstraint(
                name = "uk_tax_year_range",
                columnNames = {"tax_year", "salary_min", "salary_max"}
        ),
        indexes = @Index(
                name = "idx_tax_lookup",
                columnList = "tax_year, salary_min"
        )
)
public class TaxWithholdingTable extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long taxId;

    @Column(nullable = false)
    private Integer taxYear;

    @Column(name = "salary_min", nullable = false)
    private Integer salaryMin;       // 월급여 이상 (천원)

    @Column(name = "salary_max", nullable = false)
    private Integer salaryMax;       // 월급여 미만 (천원)

    @Column(name = "tax_dep_01", nullable = false) private Integer taxDep01;
    @Column(name = "tax_dep_02", nullable = false) private Integer taxDep02;
    @Column(name = "tax_dep_03", nullable = false) private Integer taxDep03;
    @Column(name = "tax_dep_04", nullable = false) private Integer taxDep04;
    @Column(name = "tax_dep_05", nullable = false) private Integer taxDep05;
    @Column(name = "tax_dep_06", nullable = false) private Integer taxDep06;
    @Column(name = "tax_dep_07", nullable = false) private Integer taxDep07;
    @Column(name = "tax_dep_08", nullable = false) private Integer taxDep08;
    @Column(name = "tax_dep_09", nullable = false) private Integer taxDep09;
    @Column(name = "tax_dep_10", nullable = false) private Integer taxDep10;
    @Column(name = "tax_dep_11", nullable = false) private Integer taxDep11;

    public Integer getTaxByDependents(int dependents) {
        int d = Math.min(Math.max(dependents, 1), 11);
        return switch (d) {
            case 1 -> taxDep01;
            case 2 -> taxDep02;
            case 3 -> taxDep03;
            case 4 -> taxDep04;
            case 5 -> taxDep05;
            case 6 -> taxDep06;
            case 7 -> taxDep07;
            case 8 -> taxDep08;
            case 9 -> taxDep09;
            case 10 -> taxDep10;
            case 11 -> taxDep11;
            default -> 0;
        };
    }
}

