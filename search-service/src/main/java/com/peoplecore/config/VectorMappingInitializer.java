package com.peoplecore.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class VectorMappingInitializer {

    private static final String INDEX_NAME = "unified_search";
    private static final String MAPPING_JSON = """
            {
              "properties": {
                "content_vector": {
                  "type": "dense_vector",
                  "dims": 1536,
                  "index": true,
                  "similarity": "cosine"
                }
              }
            }
            """;

    private final ElasticsearchOperations elasticsearchOperations;

    public VectorMappingInitializer(ElasticsearchOperations elasticsearchOperations) {
        this.elasticsearchOperations = elasticsearchOperations;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureVectorMapping() {
        try {
            IndexOperations indexOps = elasticsearchOperations.indexOps(IndexCoordinates.of(INDEX_NAME));

            if (!indexOps.exists()) {
                log.info("[VectorMapping] index '{}' does not exist yet — skipping (will be created on first indexing)", INDEX_NAME);
                return;
            }

            Document mapping = Document.parse(MAPPING_JSON);
            indexOps.putMapping(mapping);
            log.info("[VectorMapping] content_vector field ensured on '{}' (dense_vector, 1536 dims, cosine)", INDEX_NAME);

        } catch (Exception e) {
            log.error("[VectorMapping] failed to ensure content_vector mapping on '{}': {}", INDEX_NAME, e.getMessage(), e);
        }
    }
}
