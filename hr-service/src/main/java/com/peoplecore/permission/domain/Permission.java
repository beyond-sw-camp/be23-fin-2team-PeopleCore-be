/*
package com.peoplecore.permission.domain;

import com.peoplecore.employee.domain.EmpRole;
import com.peoplecore.employee.domain.Employee;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(jobTypeName = "permission")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(jobTypeName = "permission_id")
    private Long permissionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(jobTypeName = "emp_id", nullable = false)
    private Employee employee;

    @Column(jobTypeName = "emp_name")
    private String empName;

    @Enumerated(EnumType.STRING)
    @Column(jobTypeName = "requested_role")
    private EmpRole requestedRole;

    @Enumerated(EnumType.STRING)
    @Column(jobTypeName = "emp_current_role")
    private EmpRole currentRole;

    @Enumerated(EnumType.STRING)
    @Column(jobTypeName = "status")
    private PermissionStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(jobTypeName = "grantor_id")
    private Employee grantor;        // 권한 부여/회수 수행자

    @Column(jobTypeName = "reason")
    private String reason;

    @Column(jobTypeName = "created_at")
    private LocalDateTime createdAt;

    @Column(jobTypeName = "processed_at")
    private LocalDateTime processedAt;
}
*/
