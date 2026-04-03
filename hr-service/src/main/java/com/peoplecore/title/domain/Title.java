package com.peoplecore.title.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "title")
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Title {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "title_id")
    private Long titleId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "dept_id")
    private Long deptId;

    @Column(name = "title_name", nullable = false)
    private String titleName;

    @Column(name = "title_code", nullable = false)
    private String titleCode;

    public void update(String titleName, Long deptId) {
        this.titleName = titleName;
        this.deptId = deptId;
    }
}
