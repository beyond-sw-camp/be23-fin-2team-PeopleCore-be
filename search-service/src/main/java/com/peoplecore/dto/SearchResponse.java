package com.peoplecore.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResponse {

    private String keyword;
    private long totalHits;
    private int page;
    private int size;
    private List<SearchResultItem> items;
    private Map<String, Long> typeCounts;
}
