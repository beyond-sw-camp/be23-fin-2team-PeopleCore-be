package com.peoplecore.service;

import com.peoplecore.document.SearchDocument;
import com.peoplecore.embedding.EmbeddingClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.StringQuery;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class SearchBackfillService {

    private static final int PAGE_SIZE = 50;

    private final ElasticsearchOperations elasticsearchOperations;
    private final EmbeddingClient embeddingClient;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public SearchBackfillService(ElasticsearchOperations elasticsearchOperations, EmbeddingClient embeddingClient) {
        this.elasticsearchOperations = elasticsearchOperations;
        this.embeddingClient = embeddingClient;
    }

    // content_vector 없는 특정 type 문서를 한 페이지(최대 PAGE_SIZE개) 처리. 호출자가 0건 응답 받을 때까지 반복 호출.
    public BackfillResult backfillEmbeddings(String type) {
        if (!running.compareAndSet(false, true)) {
            log.warn("[backfill-{}] already running, refusing concurrent call", type);
            return new BackfillResult(type, 0, 0, 0L);
        }
        try {
            return doBackfill(type);
        } finally {
            running.set(false);
        }
    }

    private BackfillResult doBackfill(String type) {
        long started = System.currentTimeMillis();

        String dsl = """
                {
                  "bool": {
                    "must": [ { "term": { "type": "%s" } } ],
                    "must_not": [ { "exists": { "field": "content_vector" } } ]
                  }
                }
                """.formatted(type);

        StringQuery q = new StringQuery(dsl);
        q.setPageable(PageRequest.of(0, PAGE_SIZE));

        SearchHits<SearchDocument> hits = elasticsearchOperations.search(q, SearchDocument.class);
        if (hits.isEmpty()) {
            log.info("[backfill-{}] no documents to process", type);
            return new BackfillResult(type, 0, 0, System.currentTimeMillis() - started);
        }

        List<SearchDocument> docs = new ArrayList<>(hits.getSearchHits().size());
        List<String> texts = new ArrayList<>(hits.getSearchHits().size());
        for (SearchHit<SearchDocument> hit : hits.getSearchHits()) {
            SearchDocument d = hit.getContent();
            docs.add(d);
            texts.add(buildEmbeddingText(d));
        }

        List<float[]> vectors;
        try {
            vectors = embeddingClient.embedBatch(texts);
        } catch (Exception e) {
            log.error("[backfill-{}] batch embed failed: {}", type, e.getMessage());
            return new BackfillResult(type, 0, docs.size(), System.currentTimeMillis() - started);
        }

        log.info("[backfill-{}] received {} embeddings for {} docs", type, vectors.size(), docs.size());

        int processed = 0;
        int failed = 0;
        for (int i = 0; i < docs.size(); i++) {
            SearchDocument doc = docs.get(i);
            float[] vec = i < vectors.size() ? vectors.get(i) : null;
            int len = vec == null ? -1 : vec.length;
            log.info("[backfill-{}] doc id={}, title='{}', vector length={}", type, doc.getId(), doc.getTitle(), len);
            if (vec == null || vec.length == 0) {
                log.warn("[backfill-{}] skipping {} — empty vector", type, doc.getId());
                failed++;
                continue;
            }
            try {
                doc.setContentVector(vec);
                elasticsearchOperations.save(doc);
                processed++;
            } catch (Exception e) {
                log.warn("[backfill-{}] save failed for {}: {}", type, doc.getId(), e.getMessage(), e);
                failed++;
            }
        }

        long elapsedMs = System.currentTimeMillis() - started;
        log.info("[backfill-{}] DONE processed={}, failed={}, elapsedMs={}", type, processed, failed, elapsedMs);
        return new BackfillResult(type, processed, failed, elapsedMs);
    }

    private String buildEmbeddingText(SearchDocument d) {
        String title = d.getTitle() != null ? d.getTitle() : "";
        String content = d.getContent() != null ? d.getContent() : "";
        return (title + "\n" + content).trim();
    }

    public record BackfillResult(String type, int processed, int failed, long elapsedMs) {}
}
