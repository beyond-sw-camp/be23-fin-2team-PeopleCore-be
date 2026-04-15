package com.peoplecore.controller;

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
