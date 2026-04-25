package com.peoplecore.controller;

import com.peoplecore.service.SearchBackfillService;
import com.peoplecore.service.SearchBackfillService.BackfillResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/search/backfill")
public class SearchBackfillController {

    private final SearchBackfillService searchBackfillService;

    public SearchBackfillController(SearchBackfillService searchBackfillService) {
        this.searchBackfillService = searchBackfillService;
    }

    // content_vector 없는 문서를 임베딩해서 채움. 기본값 APPROVAL.
    // 예: POST /internal/search/backfill/embeddings?type=APPROVAL
    @PostMapping("/embeddings")
    public ResponseEntity<BackfillResult> backfillEmbeddings(
            @RequestParam(defaultValue = "APPROVAL") String type) {
        return ResponseEntity.ok(searchBackfillService.backfillEmbeddings(type));
    }
}
