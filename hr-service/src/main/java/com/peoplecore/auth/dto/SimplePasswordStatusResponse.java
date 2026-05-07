package com.peoplecore.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimplePasswordStatusResponse {

    private boolean hasPin;
    private String updatedAt;
}
