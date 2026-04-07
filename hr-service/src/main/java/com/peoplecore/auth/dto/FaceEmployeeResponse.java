package com.peoplecore.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@Builder
public class FaceEmployeeResponse {
    private Long empId;
    private String empName;
    private String empNum;
    private String deptName;
    private String gradeName;
    private boolean faceRegistered;
    private LocalDateTime registeredAt;
}
