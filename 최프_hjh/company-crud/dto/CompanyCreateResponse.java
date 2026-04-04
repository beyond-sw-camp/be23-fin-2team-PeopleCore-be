package com.peoplecore.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 회사 등록 통합 응답 DTO
 * - 회사 정보 + 관리자 계정 정보를 함께 반환
 */
@Getter
@Builder
public class CompanyCreateResponse {

    private CompanyResponse company;
    private AdminAccountResponse admin;
}
