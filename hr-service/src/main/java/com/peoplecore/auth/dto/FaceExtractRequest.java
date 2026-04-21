package com.peoplecore.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class FaceExtractRequest {
    private String image; // base64 인코딩된 이미지
}
