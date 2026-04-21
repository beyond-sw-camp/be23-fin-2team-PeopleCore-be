package com.peoplecore.auth.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
public class FaceLoginRequest {
    private String image; // base64 인코딩된 이미지
    private UUID companyId;
}
