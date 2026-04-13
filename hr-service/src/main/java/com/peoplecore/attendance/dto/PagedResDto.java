package com.peoplecore.attendance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 관리자 근태 API 공통 페이지네이션 응답.
 * 인계서 명세의 공통 포맷(content / page / size / totalElements / totalPages) 에 맞춤.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PagedResDto<T> {

    /** 현재 페이지 content */
    private List<T> content;

    /** 현재 페이지 번호 (0-based) */
    private int page;

    /** 페이지 크기 */
    private int size;

    /** 전체 건수 */
    private long totalElements;

    /** 전체 페이지 수 */
    private int totalPages;
}