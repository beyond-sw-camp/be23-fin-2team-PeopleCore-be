package com.peoplecore.auth.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "login_history", indexes = {
        @Index(name = "idx_login_history_emp", columnList = "emp_id, login_at DESC")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoginHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "emp_id", nullable = false)
    private Long empId;

    @Column(name = "ip", length = 45)
    private String ip;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "login_method", length = 20)
    private String loginMethod;   // "PASSWORD" | "FACE"

    @Column(name = "login_at", nullable = false)
    private LocalDateTime loginAt;

    @Builder
    public LoginHistory(Long empId, String ip, String userAgent, String loginMethod) {
        this.empId = empId;
        this.ip = ip;
        this.userAgent = userAgent;
        this.loginMethod = loginMethod;
        this.loginAt = LocalDateTime.now();
    }
}