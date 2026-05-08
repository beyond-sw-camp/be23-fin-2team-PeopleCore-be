package com.peoplecore.service;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import com.peoplecore.document.SearchDocument;
import com.peoplecore.dto.SearchResponse;
import com.peoplecore.dto.SearchResultItem;
import com.peoplecore.dto.SuggestItem;
import com.peoplecore.dto.SuggestResponse;
import com.peoplecore.embedding.EmbeddingClient;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private static final int RRF_K = 60;
    private static final int HYBRID_TOP_K = 50;

    // 4곳(search/countByType/suggest)에서 동일 필드를 쓰던 것을 상수로 묶음.
    // 새 검색 필드는 여기 한 줄만 추가하면 BM25 경로 전체에 일관 적용된다.
    private static final List<String> SEARCH_FIELDS = List.of(
            "title^3", "title.ngram",
            "content",
            "metadata.empName^2", "metadata.empName.ngram",
            "metadata.deptName^2", "metadata.deptName.ngram",
            "metadata.gradeName", "metadata.titleName",
            "metadata.docNum", "metadata.location",
            "metadata.deptCode"
    );

    // suggest 는 자동완성 용도라 본문(content)·부가 직급(gradeName/titleName) 제외.
    private static final List<String> SUGGEST_FIELDS = List.of(
            "title^3", "title.ngram",
            "metadata.empName^2", "metadata.empName.ngram",
            "metadata.deptName^2", "metadata.deptName.ngram",
            "metadata.docNum", "metadata.location",
            "metadata.deptCode"
    );

    private final ElasticsearchOperations elasticsearchOperations;
    private final SearchRepository searchRepository;
    private final EmbeddingClient embeddingClient;

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

    /**
     * BM25 + kNN + RRF(k=60) 하이브리드 검색.
     * - BM25: 기존 buildSearchQuery 재사용 (multiMatch)
     * - kNN : 사용자 질의를 1536d 벡터로 임베딩 후 content_vector 코사인 검색
     * - 융합: RRF score(d) = Σ 1 / (RRF_K + rank_i(d))  for each branch
     * 회사/권한 필터는 양 쪽에 동일하게 적용한다.
     */
    public SearchResponse searchHybrid(String keyword, String type, String companyId,
                                       Long empId, String role, int size) {
        boolean isAdmin = isAdmin(role);
        int topK = HYBRID_TOP_K;

        NativeQuery bm25Q = buildSearchQuery(keyword, type, companyId, empId, isAdmin, 0, topK);
        SearchHits<SearchDocument> bm25Hits = elasticsearchOperations.search(bm25Q, SearchDocument.class);

        float[] queryVector;
        try {
            queryVector = embeddingClient.embed(keyword);
        } catch (Exception e) {
            log.warn("[hybrid] query embedding failed ({}); falling back to BM25-only", e.getMessage());
            return search(keyword, type, companyId, empId, null, role, 0, size);
        }

        SearchHits<SearchDocument> knnHits;
        if (queryVector == null || queryVector.length == 0) {
            log.warn("[hybrid] empty query vector; falling back to BM25-only");
            knnHits = null;
        } else {
            NativeQuery knnQ = buildKnnQuery(queryVector, type, companyId, empId, isAdmin, topK);
            knnHits = elasticsearchOperations.search(knnQ, SearchDocument.class);
        }

        Map<String, Double> rrfScore = new HashMap<>();
        Map<String, SearchHit<SearchDocument>> bestHit = new LinkedHashMap<>();
        Map<String, Integer> bm25Rank = new HashMap<>();
        Map<String, Integer> knnRank = new HashMap<>();

        accumulateRrf(bm25Hits, rrfScore, bestHit, bm25Rank);
        if (knnHits != null) {
            accumulateRrf(knnHits, rrfScore, bestHit, knnRank);
        }

        List<SearchResultItem> items = rrfScore.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(size)
                .map(e -> {
                    SearchHit<SearchDocument> hit = bestHit.get(e.getKey());
                    return toHybridResultItem(hit, e.getValue(), bm25Rank.get(e.getKey()), knnRank.get(e.getKey()));
                })
                .toList();

        Map<String, Long> typeCounts = aggregateTypeCounts(bestHit, rrfScore);

        return SearchResponse.builder()
                .keyword(keyword)
                .totalHits(rrfScore.size())
                .page(0)
                .size(size)
                .items(items)
                .typeCounts(typeCounts)
                .build();
    }

    private void accumulateRrf(SearchHits<SearchDocument> hits,
                               Map<String, Double> rrfScore,
                               Map<String, SearchHit<SearchDocument>> bestHit,
                               Map<String, Integer> rankByDoc) {
        int rank = 0;
        for (SearchHit<SearchDocument> hit : hits.getSearchHits()) {
            rank++;
            String id = hit.getId();
            rrfScore.merge(id, 1.0 / (RRF_K + rank), Double::sum);
            bestHit.putIfAbsent(id, hit);
            rankByDoc.put(id, rank);
        }
    }

    private NativeQuery buildKnnQuery(float[] vector, String type, String companyId,
                                      Long empId, boolean isAdmin, int k) {
        List<Float> queryVector = new ArrayList<>(vector.length);
        for (float f : vector) queryVector.add(f);
        int numCandidates = Math.max(k * 5, 100);

        return NativeQuery.builder()
                .withKnnSearches(knn -> knn
                        .field("content_vector")
                        .queryVector(queryVector)
                        .k(k)
                        .numCandidates(numCandidates)
                        .filter(f -> f.bool(b -> applyScope(b, type, companyId, empId, isAdmin)))
                )
                .withMaxResults(k)
                .build();
    }

    private SearchResultItem toHybridResultItem(SearchHit<SearchDocument> hit, double rrf,
                                                Integer bm25Rank, Integer knnRank) {
        SearchDocument doc = hit.getContent();
        Map<String, List<String>> highlights = hit.getHighlightFields();
        Map<String, Object> meta = doc.getMetadata() != null ? new HashMap<>(doc.getMetadata()) : new HashMap<>();
        if (bm25Rank != null) meta.put("_bm25Rank", bm25Rank);
        if (knnRank != null) meta.put("_knnRank", knnRank);
        return SearchResultItem.builder()
                .id(doc.getId())
                .type(doc.getType())
                .sourceId(doc.getSourceId())
                .title(doc.getTitle())
                .content(doc.getContent())
                .metadata(meta)
                .createdAt(doc.getCreatedAt())
                .score((float) rrf)
                .highlights(highlights == null || highlights.isEmpty() ? null : highlights)
                .build();
    }

    private Map<String, Long> aggregateTypeCounts(Map<String, SearchHit<SearchDocument>> bestHit,
                                                  Map<String, Double> rrfScore) {
        Map<String, Long> counts = new HashMap<>();
        for (String t : List.of("EMPLOYEE", "DEPARTMENT", "APPROVAL", "CALENDAR")) {
            counts.put(t, 0L);
        }
        for (String id : rrfScore.keySet()) {
            SearchHit<SearchDocument> hit = bestHit.get(id);
            if (hit == null) continue;
            String t = hit.getContent().getType();
            counts.merge(t, 1L, Long::sum);
        }
        return counts;
    }

    /**
     * 색인 진입점. 호출자(주로 CdcEventListener의 각 핸들러)는 도메인 지식만 — title/content/metadata 조립 — 만 책임지고,
     * 임베딩 호출/실패 처리/모델 선택 같은 인프라는 여기 한 곳으로 일원화한다.
     * 이미 contentVector가 채워진 doc(예: 결재선 갱신 같은 부분 재색인)은 재임베딩하지 않고 그대로 보존.
     */
    public void indexDocument(SearchDocument document) {
        if (document.getContentVector() == null) {
            document.setContentVector(embedSafe(document.getTitle(), document.getContent()));
        }
        searchRepository.save(document);
        log.info("Indexed document: type={}, sourceId={}", document.getType(), document.getSourceId());
    }

    public void deleteDocument(String sourceId, String type) {
        searchRepository.deleteBySourceIdAndType(sourceId, type);
        log.info("Deleted document: type={}, sourceId={}", type, sourceId);
    }

    // 임베딩 실패해도 색인은 진행 (벡터 없는 문서는 BM25로만 검색됨).
    // CdcEventListener에서 옮겨와 indexDocument의 보조로 일원화.
    private float[] embedSafe(String title, String content) {
        try {
            String combined = ((title != null ? title : "") + "\n" + (content != null ? content : "")).trim();
            if (combined.isBlank()) return null;
            return embeddingClient.embed(combined);
        } catch (Exception e) {
            log.warn("Embedding failed, indexing without vector: {}", e.getMessage());
            return null;
        }
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
                            applyScope(b, type, companyId, empId, isAdmin);
                            b.must(m -> m
                                    .multiMatch(mm -> mm
                                            .query(keyword)
                                            .fields(SEARCH_FIELDS)
                                            .operator(co.elastic.clients.elasticsearch._types.query_dsl.Operator.And)
                                    )
                            );
                            return b;
                        })
                )
                .withPageable(PageRequest.of(page, size))
                .build();
    }

    /**
     * 회사/타입/권한 필터를 BoolQuery에 일괄 적용. BM25·kNN·countByType·suggest 모두 이 헬퍼만 호출하도록 통일하여,
     * 한쪽에만 필터가 빠져 권한 누수가 일어나는 사고를 구조적으로 차단한다.
     */
    private co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.Builder applyScope(
            co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.Builder b,
            String type, String companyId, Long empId, boolean isAdmin) {
        b.filter(f -> f.term(t -> t.field("companyId").value(companyId)));
        if (type != null && !type.isBlank()) {
            b.filter(f -> f.term(t -> t.field("type").value(type)));
        }
        if (!isAdmin) {
            b.filter(f -> f.bool(ab -> applyAccessFilter(ab, empId)));
        }
        return b;
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
                            applyScope(b, null, companyId, empId, isAdmin);
                            b.must(m -> m
                                    .multiMatch(mm -> mm
                                            .query(keyword)
                                            .fields(SEARCH_FIELDS)
                                            .operator(co.elastic.clients.elasticsearch._types.query_dsl.Operator.And)
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

    /**
     * 검색어 자동완성. 전 타입(EMPLOYEE/DEPARTMENT/APPROVAL/CALENDAR) 혼합 Top-N.
     * 메인 검색과 동일한 권한/회사 필터를 적용하여 일관성 유지.
     */
    public SuggestResponse suggest(String keyword, String companyId, Long empId, String role, int size) {
        boolean isAdmin = isAdmin(role);
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q
                        .bool(b -> {
                            applyScope(b, null, companyId, empId, isAdmin);
                            b.must(m -> m
                                    .multiMatch(mm -> mm
                                            .query(keyword)
                                            .fields(SUGGEST_FIELDS)
                                            .operator(co.elastic.clients.elasticsearch._types.query_dsl.Operator.And)
                                    )
                            );
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
