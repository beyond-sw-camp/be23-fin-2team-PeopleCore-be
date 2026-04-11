package com.peoplecore.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResultItem {

    private String id;
    private String type;
    private String sourceId;
    private String title;
    private String content;
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;
    private float score;
}
