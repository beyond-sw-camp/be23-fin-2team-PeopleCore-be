package com.peoplecore.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

public class HrAdminPinDtos {

    @Getter @NoArgsConstructor @AllArgsConstructor
    public static class StatusResponse {
        private boolean hasPin;
        private LocalDateTime updatedAt;
    }

    @Getter @NoArgsConstructor
    public static class SetRequest {
        private String loginPassword;
        private String newPin;
    }

    @Getter @NoArgsConstructor
    public static class ChangeRequest {
        private String currentPin;
        private String newPin;
    }

    @Getter @NoArgsConstructor
    public static class DeleteRequest {
        private String loginPassword;
    }

    @Getter @NoArgsConstructor
    public static class VerifyRequest {
        private String pin;
    }

    @Getter @NoArgsConstructor @AllArgsConstructor
    public static class VerifyResponse {
        private String hrAdminToken;
        private long expiresInSeconds;
    }
}
