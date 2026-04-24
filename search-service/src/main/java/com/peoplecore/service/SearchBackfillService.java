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

@Slf4j
@Service
public class SearchBackfillService {

    private static final int PAGE_SIZE = 50;

    private final ElasticsearchOperations elasticsearchOperations;
    private final EmbeddingClient embeddingClient;

    public SearchBackfillService(ElasticsearchOperations elasticsearchOperations, EmbeddingClient embeddingClient) {
        this.elasticsearchOperations = elasticsearchOperations;
        this.embeddingClient = embeddingClient;
    }

    // content_vector 없는 특정 type 문서를 조회해 임베딩을 채워넣음. 멱등적 — 중단 후 재호출 가능.
    public BackfillResult backfillEmbeddings(String type) {
        int processed = 0;
        int failed = 0;
        int round = 0;
        long started = System.currentTimeMillis();

        while (true) {
            round++;
            String dsl = """
                    {
                      "query": {
                        "bool": {
                          "must": [ { "term": { "type": "%s" } } ],
                          "must_not": [ { "exists": { "field": "content_vector" } } ]
                        }
                      }
                    }
                    """.formatted(type);

            StringQuery q = new StringQuery(dsl);
            q.setPageable(PageRequest.of(0, PAGE_SIZE));

            SearchHits<SearchDocument> hits = elasticsearchOperations.search(q, SearchDocument.class);
            if (hits.isEmpty()) break;

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
                log.error("[backfill-{}] batch embed failed at round={}, aborting: {}", type, round, e.getMessage());
                failed += docs.size();
                break;
            }

            for (int i = 0; i < docs.size(); i++) {
                try {
                    SearchDocument doc = docs.get(i);
                    doc.setContentVector(vectors.get(i));
                    elasticsearchOperations.save(doc);
                    processed++;
                } catch (Exception e) {
                    log.warn("[backfill-{}] save failed for {}: {}", type, docs.get(i).getId(), e.getMessage());
                    failed++;
                }
            }

            log.info("[backfill-{}] round={}, processed={}, failed={}, batchSize={}",
                    type, round, processed, failed, docs.size());
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
