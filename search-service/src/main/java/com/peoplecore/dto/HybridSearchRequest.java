package com.peoplecore.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * POST /search/hybrid 요청 body. GET 변형은 querystring 한계(길이/로깅/구조표현)로 인해
 * Copilot/긴 컨텍스트 시나리오에는 부적합 — 같은 검색 로직을 body 기반으로 노출.
 * 향후 필터(부서·기간·doctype 등)·대화 컨텍스트 추가 시 이 DTO에 필드만 늘리면 된다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HybridSearchRequest {

    private String keyword;
    private String type;   // null이면 전 타입
    private Integer size;  // null이면 기본 10

    public int sizeOrDefault() {
        return size == null || size <= 0 ? 10 : size;
    }
}
