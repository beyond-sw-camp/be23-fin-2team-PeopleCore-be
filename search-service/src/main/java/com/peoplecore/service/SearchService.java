package com.peoplecore.service;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import com.peoplecore.document.SearchDocument;
import com.peoplecore.dto.SearchResponse;
import com.peoplecore.dto.SearchResultItem;
import com.peoplecore.dto.SuggestItem;
import com.peoplecore.dto.SuggestResponse;
import com.peoplecore.repository.SearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightFieldParameters;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
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

    public SearchResponse search(String keyword, String type, String companyId,
                                 Long empId, Long deptId, String role,
                                 int page, int size) {
        boolean isAdmin = isAdmin(role);
        NativeQuery query = buildSearchQuery(keyword, type, companyId, empId, isAdmin, page, size);
        SearchHits<SearchDocument> searchHits = elasticsearchOperations.search(query, SearchDocument.class);

        List<SearchResultItem> items = searchHits.getSearchHits().stream()
                .map(this::toResultItem)
                .toList();

        Map<String, Long> typeCounts = countByType(keyword, companyId, empId, isAdmin);

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

    private static final List<String> HIGHLIGHT_FIELDS = List.of(
            "title", "content",
            "metadata.empName", "metadata.deptName",
            "metadata.gradeName", "metadata.titleName",
            "metadata.docNum", "metadata.location"
    );

    /**
     * ngram 멀티필드까지 원본 필드와 매칭되도록 require_field_match=false.
     * title/metadata.* 는 number_of_fragments=0 로 전체 텍스트에 태그만 삽입 (제목·부가정보용),
     * content 만 short snippet.
     */
    private Highlight buildHighlight() {
        HighlightParameters params = HighlightParameters.builder()
                .withPreTags("<em>")
                .withPostTags("</em>")
                .withRequireFieldMatch(false)
                .build();

        List<HighlightField> fields = HIGHLIGHT_FIELDS.stream()
                .map(name -> {
                    HighlightFieldParameters.HighlightFieldParametersBuilder fp =
                            HighlightFieldParameters.builder();
                    if ("content".equals(name)) {
                        fp.withNumberOfFragments(1).withFragmentSize(120);
                    } else {
                        fp.withNumberOfFragments(0); // 전체 필드 반환, 태그만 삽입
                    }
                    return new HighlightField(name, fp.build());
                })
                .toList();

        return new Highlight(params, fields);
    }

    private NativeQuery buildSearchQuery(String keyword, String type, String companyId,
                                         Long empId, boolean isAdmin, int page, int size) {
        return NativeQuery.builder()
                .withHighlightQuery(new HighlightQuery(buildHighlight(), SearchDocument.class))
                .withQuery(q -> q
                        .bool(b -> {
                            b.filter(f -> f.term(t -> t.field("companyId").value(companyId)));

                            if (type != null && !type.isBlank()) {
                                b.filter(f -> f.term(t -> t.field("type").value(type)));
                            }

                            b.must(m -> m
                                    .multiMatch(mm -> mm
                                            .query(keyword)
                                            .fields("title^3", "title.ngram",
                                                    "content",
                                                    "metadata.empName^2", "metadata.empName.ngram",
                                                    "metadata.deptName^2", "metadata.deptName.ngram",
                                                    "metadata.gradeName", "metadata.titleName",
                                                    "metadata.docNum", "metadata.location")
                                    )
                            );

                            if (!isAdmin) {
                                b.filter(f -> f.bool(ab -> applyAccessFilter(ab, empId)));
                            }

                            return b;
                        })
                )
                .withPageable(PageRequest.of(page, size))
                .build();
    }

    /**
     * 권한 필터. type별 접근 규칙을 should로 OR 연결.
     * - EMPLOYEE: metadata.empStatus=ACTIVE (휴직자/퇴사자 제외)
     * - DEPARTMENT: metadata.isUse=true
     * - APPROVAL: drafterId==me OR me ∈ accessibleEmpIds
     * - CALENDAR: ownerId==me OR isPublic=true OR isAllEmployees=true
     * 관리자(HR_ADMIN/HR_SUPER_ADMIN)는 이 필터를 스킵 (isAdmin=true)
     */
    private co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.Builder applyAccessFilter(
            co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.Builder ab, Long empId) {

        ab.should(s -> s.bool(eb -> eb
                .must(m -> m.term(t -> t.field("type").value("EMPLOYEE")))
                .must(m -> m.term(t -> t.field("metadata.empStatus").value("ACTIVE")))
        ));

        ab.should(s -> s.bool(eb -> eb
                .must(m -> m.term(t -> t.field("type").value("DEPARTMENT")))
                .must(m -> m.term(t -> t.field("metadata.isUse").value(true)))
        ));

        ab.should(s -> s.bool(eb -> eb
                .must(m -> m.term(t -> t.field("type").value("APPROVAL")))
                .must(m -> m.bool(ob -> ob
                        .should(ss -> ss.term(t -> t.field("metadata.drafterId").value(empId)))
                        .should(ss -> ss.term(t -> t.field("metadata.accessibleEmpIds").value(empId)))
                        .minimumShouldMatch("1")
                ))
        ));

        ab.should(s -> s.bool(eb -> eb
                .must(m -> m.term(t -> t.field("type").value("CALENDAR")))
                .must(m -> m.bool(ob -> ob
                        .should(ss -> ss.term(t -> t.field("metadata.ownerId").value(empId)))
                        .should(ss -> ss.term(t -> t.field("metadata.isPublic").value(true)))
                        .should(ss -> ss.term(t -> t.field("metadata.isAllEmployees").value(true)))
                        .minimumShouldMatch("1")
                ))
        ));

        return ab.minimumShouldMatch("1");
    }

    private boolean isAdmin(String role) {
        return "HR_SUPER_ADMIN".equals(role) || "HR_ADMIN".equals(role);
    }

    private Map<String, Long> countByType(String keyword, String companyId, Long empId, boolean isAdmin) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q
                        .bool(b -> {
                            b.filter(f -> f.term(t -> t.field("companyId").value(companyId)));
                            b.must(m -> m
                                    .multiMatch(mm -> mm
                                            .query(keyword)
                                            .fields("title^3", "title.ngram",
                                                    "content",
                                                    "metadata.empName^2", "metadata.empName.ngram",
                                                    "metadata.deptName^2", "metadata.deptName.ngram",
                                                    "metadata.gradeName", "metadata.titleName",
                                                    "metadata.docNum", "metadata.location")
                                    )
                            );
                            if (!isAdmin) {
                                b.filter(f -> f.bool(ab -> applyAccessFilter(ab, empId)));
                            }
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

    /**
     * 검색어 자동완성. 전 타입(EMPLOYEE/DEPARTMENT/APPROVAL/CALENDAR) 혼합 Top-N.
     * 메인 검색과 동일한 권한/회사 필터를 적용하여 일관성 유지.
     */
    public SuggestResponse suggest(String keyword, String companyId, Long empId, String role, int size) {
        boolean isAdmin = isAdmin(role);
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q
                        .bool(b -> {
                            b.filter(f -> f.term(t -> t.field("companyId").value(companyId)));
                            b.must(m -> m
                                    .multiMatch(mm -> mm
                                            .query(keyword)
                                            .fields("title^3", "title.ngram",
                                                    "metadata.empName^2", "metadata.empName.ngram",
                                                    "metadata.deptName^2", "metadata.deptName.ngram",
                                                    "metadata.docNum", "metadata.location")
                                    )
                            );
                            if (!isAdmin) {
                                b.filter(f -> f.bool(ab -> applyAccessFilter(ab, empId)));
                            }
                            return b;
                        })
                )
                .withPageable(PageRequest.of(0, size))
                .build();

        SearchHits<SearchDocument> hits = elasticsearchOperations.search(query, SearchDocument.class);

        List<SuggestItem> items = hits.getSearchHits().stream()
                .map(this::toSuggestItem)
                .toList();

        return SuggestResponse.builder()
                .keyword(keyword)
                .items(items)
                .build();
    }

    private SuggestItem toSuggestItem(SearchHit<SearchDocument> hit) {
        SearchDocument doc = hit.getContent();
        Map<String, Object> meta = doc.getMetadata() != null ? doc.getMetadata() : Map.of();
        String subLabel = switch (doc.getType()) {
            case "EMPLOYEE" -> joinNonBlank(
                    asString(meta.get("deptName")),
                    asString(meta.get("gradeName")),
                    asString(meta.get("titleName"))
            );
            case "DEPARTMENT" -> asString(meta.get("deptCode"));
            case "APPROVAL" -> joinNonBlank(
                    asString(meta.get("docNum")),
                    asString(meta.get("empName"))
            );
            case "CALENDAR" -> asString(meta.get("location"));
            default -> null;
        };
        return SuggestItem.builder()
                .type(doc.getType())
                .sourceId(doc.getSourceId())
                .title(doc.getTitle())
                .subLabel(subLabel)
                .link(asString(meta.get("link")))
                .build();
    }

    private String asString(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private String joinNonBlank(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isBlank()) continue;
            if (sb.length() > 0) sb.append(" · ");
            sb.append(p);
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private SearchResultItem toResultItem(SearchHit<SearchDocument> hit) {
        SearchDocument doc = hit.getContent();
        Map<String, List<String>> highlights = hit.getHighlightFields();
        return SearchResultItem.builder()
                .id(doc.getId())
                .type(doc.getType())
                .sourceId(doc.getSourceId())
                .title(doc.getTitle())
                .content(doc.getContent())
                .metadata(doc.getMetadata())
                .createdAt(doc.getCreatedAt())
                .score(hit.getScore())
                .highlights(highlights == null || highlights.isEmpty() ? null : highlights)
                .build();
    }
}
