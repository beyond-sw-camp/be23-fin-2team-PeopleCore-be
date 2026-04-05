package com.peoplecore.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

/**
 * 최고관리자 계정 생성 결과 DTO
 * - 회사 UUID와 함께 생성된 관리자 계정 정보 반환
 * - 임시 비밀번호는 이메일로만 전달 (응답에는 포함하지 않음)
 */
@Getter
@Builder
public class AdminAccountResponse {

    private UUID companyId;
    private String adminEmail;
    private String adminName;
    private String message; // "임시 비밀번호가 이메일로 전달되었습니다"
}
