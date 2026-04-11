package com.peoplecore.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.time.LocalDateTime;
import java.util.Map;

@Document(indexName = "unified_search")
@Setting(settingPath = "/elasticsearch/settings.json")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String type;

    @Field(type = FieldType.Keyword)
    private String companyId;

    @Field(type = FieldType.Keyword)
    private String sourceId;

    @Field(type = FieldType.Text, analyzer = "korean", searchAnalyzer = "korean_search")
    private String title;

    @Field(type = FieldType.Text, analyzer = "korean", searchAnalyzer = "korean_search")
    private String content;

    @Field(type = FieldType.Object)
    private Map<String, Object> metadata;

    @Field(type = FieldType.Date)
    private LocalDateTime createdAt;
}
