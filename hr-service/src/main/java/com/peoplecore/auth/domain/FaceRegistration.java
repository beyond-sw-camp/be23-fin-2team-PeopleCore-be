package com.peoplecore.auth.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "face_registration")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FaceRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "emp_id", nullable = false, unique = true)
    private Long empId;

    @Column(name = "emp_name", nullable = false, length = 50)
    private String empName;

    @Column(name = "registered_at", nullable = false)
    private LocalDateTime registeredAt;

    @PrePersist
    public void prePersist() {
        this.registeredAt = LocalDateTime.now();
    }
}
