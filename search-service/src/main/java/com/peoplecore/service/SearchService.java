package com.peoplecore.service;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import com.peoplecore.document.SearchDocument;
import com.peoplecore.dto.SearchResponse;
import com.peoplecore.dto.SearchResultItem;
import com.peoplecore.repository.SearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final ElasticsearchOperations elasticsearchOperations;
    private final SearchRepository searchRepository;

    public SearchResponse search(String keyword, String type, String companyId, int page, int size) {
        NativeQuery query = buildSearchQuery(keyword, type, companyId, page, size);
        SearchHits<SearchDocument> searchHits = elasticsearchOperations.search(query, SearchDocument.class);

        List<SearchResultItem> items = searchHits.getSearchHits().stream()
                .map(this::toResultItem)
                .toList();

        Map<String, Long> typeCounts = countByType(keyword, companyId);

        return SearchResponse.builder()
                .keyword(keyword)
                .totalHits(searchHits.getTotalHits())
                .page(page)
                .size(size)
                .items(items)
                .typeCounts(typeCounts)
                .build();
    }

    public void indexDocument(SearchDocument document) {
        searchRepository.save(document);
        log.info("Indexed document: type={}, sourceId={}", document.getType(), document.getSourceId());
    }

    public void deleteDocument(String sourceId, String type) {
        searchRepository.deleteBySourceIdAndType(sourceId, type);
        log.info("Deleted document: type={}, sourceId={}", type, sourceId);
    }

    private NativeQuery buildSearchQuery(String keyword, String type, String companyId, int page, int size) {
        return NativeQuery.builder()
                .withQuery(q -> q
                        .bool(b -> {
                            b.filter(f -> f.term(t -> t.field("companyId").value(companyId)));

                            if (type != null && !type.isBlank()) {
                                b.filter(f -> f.term(t -> t.field("type").value(type)));
                            }

                            b.must(m -> m
                                    .multiMatch(mm -> mm
                                            .query(keyword)
                                            .fields("title^3", "content",
                                                    "metadata.empName^2", "metadata.deptName^2",
                                                    "metadata.gradeName", "metadata.titleName",
                                                    "metadata.docNum", "metadata.location")
                                            .fuzziness("AUTO")
                                    )
                            );

                            return b;
                        })
                )
                .withPageable(PageRequest.of(page, size))
                .build();
    }

    private Map<String, Long> countByType(String keyword, String companyId) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q
                        .bool(b -> {
                            b.filter(f -> f.term(t -> t.field("companyId").value(companyId)));
                            b.must(m -> m
                                    .multiMatch(mm -> mm
                                            .query(keyword)
                                            .fields("title^3", "content",
                                                    "metadata.empName^2", "metadata.deptName^2",
                                                    "metadata.gradeName", "metadata.titleName",
                                                    "metadata.docNum", "metadata.location")
                                    )
                            );
                            return b;
                        })
                )
                .withAggregation("type_counts",
                        Aggregation.of(a -> a.terms(t -> t.field("type")))
                )
                .withMaxResults(0)
                .build();

        SearchHits<SearchDocument> hits = elasticsearchOperations.search(query, SearchDocument.class);

        Map<String, Long> counts = new HashMap<>();
        if (hits.getAggregations() != null) {
            try {
                ElasticsearchAggregations aggs = (ElasticsearchAggregations) hits.getAggregations();
                List<StringTermsBucket> buckets = aggs.get("type_counts")
                        .aggregation()
                        .getAggregate()
                        .sterms()
                        .buckets()
                        .array();
                for (StringTermsBucket bucket : buckets) {
                    counts.put(bucket.key().stringValue(), bucket.docCount());
                }
            } catch (Exception e) {
                log.warn("Failed to parse type_counts aggregation", e);
            }
        }

        for (String t : List.of("EMPLOYEE", "DEPARTMENT", "APPROVAL", "CALENDAR")) {
            counts.putIfAbsent(t, 0L);
        }

        return counts;
    }

    private SearchResultItem toResultItem(SearchHit<SearchDocument> hit) {
        SearchDocument doc = hit.getContent();
        return SearchResultItem.builder()
                .id(doc.getId())
                .type(doc.getType())
                .sourceId(doc.getSourceId())
                .title(doc.getTitle())
                .content(doc.getContent())
                .metadata(doc.getMetadata())
                .createdAt(doc.getCreatedAt())
                .score(hit.getScore())
                .build();
    }
}
