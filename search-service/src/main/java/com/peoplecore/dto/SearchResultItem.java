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
public class SearchResultItem {

    private String id;
    private String type;
    private String sourceId;
    private String title;
    private String content;
    private Map<String, Object> metadata;
    private String createdAt;
    private float score;

    /** ES highlight fragments. key = field name (e.g. "title", "metadata.empName"). */
    private Map<String, List<String>> highlights;
}
