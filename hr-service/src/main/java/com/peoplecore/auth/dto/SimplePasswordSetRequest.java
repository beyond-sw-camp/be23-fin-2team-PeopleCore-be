package com.peoplecore.auth.dto;

import lombok.Getter;

@Getter
public class SimplePasswordSetRequest {
    private String loginPassword;
    private String newPin;
}