package com.peoplecore.auth.dto;

import lombok.Getter;

@Getter
public class SimplePasswordChangeRequest {
    private String currentPin;
    private String newPin;
}