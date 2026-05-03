package com.peoplecore.department.domain;

import com.peoplecore.company.domain.Company;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "department")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "dept_id")
    private Long deptId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "parent_dept_id")
    private Long parentDeptId;

    @Column(name = "dept_name", nullable = false)
    private String deptName;

    @Column(name = "dept_code", nullable = false)
    private String deptCode;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_use", nullable = false)
    @Builder.Default
    private UseStatus isUse = UseStatus.Y;

    public void updateName(String deptName) {
        this.deptName = deptName;
    }

    public void updateCode(String deptCode) {
        this.deptCode = deptCode;
    }

    public void updateParent(Long parentDeptId) {
        this.parentDeptId = parentDeptId;
    }

    public void updateSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public void updatePositionAndOrder(Long parentDeptId, Integer sortOrder) {
        this.parentDeptId = parentDeptId;
        this.sortOrder = sortOrder;
    }

    public void deactivate() {
        this.isUse = UseStatus.N;
    }

    public void activate() {
        this.isUse = UseStatus.Y;
    }
}
