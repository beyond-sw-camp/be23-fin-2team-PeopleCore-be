package com.peoplecore.permission.domain;

import com.peoplecore.employee.domain.EmpRole;
import com.peoplecore.employee.domain.Employee;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "permission")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "permission_id")
    private Long permissionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emp_id", nullable = false)
    private Employee employee;

    @Column(name = "emp_name")
    private String empName;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_role")
    private EmpRole requestedRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "emp_current_role")
    private EmpRole currentRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private PermissionStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grantor_id")
    private Employee grantor;        // 권한 부여/회수 수행자

    @Column(name = "reason")
    private String reason;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;
}
