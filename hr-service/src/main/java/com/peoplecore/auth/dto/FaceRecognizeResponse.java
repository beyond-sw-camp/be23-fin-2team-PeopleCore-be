package com.peoplecore.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class FaceRecognizeResponse {
    private boolean matched;

    @JsonProperty("emp_id")
    private Long empId;

    @JsonProperty("emp_name")
    private String empName;

    private double distance;
}
