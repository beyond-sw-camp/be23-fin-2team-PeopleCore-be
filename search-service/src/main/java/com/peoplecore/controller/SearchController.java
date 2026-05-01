package com.peoplecore.controller;

import com.peoplecore.dto.HybridSearchRequest;
import com.peoplecore.dto.SearchResponse;
import com.peoplecore.dto.SuggestResponse;
import com.peoplecore.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping
    public ResponseEntity<SearchResponse> search(
            @RequestParam String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader("X-User-Company") String companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestHeader(value = "X-User-Department", required = false) Long deptId,
            @RequestHeader("X-User-Role") String role
    ) {
        SearchResponse response = searchService.search(keyword, type, companyId, empId, deptId, role, page, size);
        return ResponseEntity.ok(response);
    }

    /**
     * 하이브리드 검색: BM25(키워드) + kNN(의미) → RRF(k=60) 융합.
     * GET — 디버깅·벤치마크 용도 (curl/브라우저로 빠르게 확인).
     */
    @GetMapping("/hybrid")
    public ResponseEntity<SearchResponse> hybridGet(
            @RequestParam String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader("X-User-Company") String companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestHeader("X-User-Role") String role
    ) {
        SearchResponse response = searchService.searchHybrid(keyword, type, companyId, empId, role, size);
        return ResponseEntity.ok(response);
    }

    /**
     * 하이브리드 검색 (POST). LLM Copilot/긴 컨텍스트용. body로 필터·대화 컨텍스트 확장 여지 보존.
     * 권한·회사 컨텍스트는 헤더 유지 — gateway가 강제 주입하는 책임 경계 일관성 위해.
     */
    @PostMapping("/hybrid")
    public ResponseEntity<SearchResponse> hybridPost(
            @RequestBody HybridSearchRequest req,
            @RequestHeader("X-User-Company") String companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestHeader("X-User-Role") String role
    ) {
        SearchResponse response = searchService.searchHybrid(
                req.getKeyword(), req.getType(), companyId, empId, role, req.sizeOrDefault());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/suggest")
    public ResponseEntity<SuggestResponse> suggest(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "8") int size,
            @RequestHeader("X-User-Company") String companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestHeader("X-User-Role") String role
    ) {
        SuggestResponse response = searchService.suggest(keyword, companyId, empId, role, size);
        return ResponseEntity.ok(response);
    }
}
