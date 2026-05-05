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

// 인덱스 자동 생성 끔 — analyzer 정의(nori, korean, korean_ngram)는
// scripts/search/es-index-mapping.json + search-init 컨테이너가 책임지고,
// 여기서는 이미 만들어진 인덱스에 read/write만 한다
@Document(indexName = "unified_search", createIndex = false)
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
    @Field(name = "content_vector", type = FieldType.Dense_Vector, dims = 1536)
    private float[] contentVector;
}
