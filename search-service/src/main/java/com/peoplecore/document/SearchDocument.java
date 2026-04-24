package com.peoplecore.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.Map;

@Document(indexName = "unified_search")
@Getter
@Setter
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

    @Field(type = FieldType.Date, format = DateFormat.date_optional_time)
    private String createdAt;

    // AI Copilot: OpenAI text-embedding-3-small (1536 dims, cosine similarity)
    // 실제 매핑(similarity/index_options)은 PUT _mapping 스크립트에서 관리
    @Field(type = FieldType.Dense_Vector, dims = 1536)
    private float[] contentVector;
}
