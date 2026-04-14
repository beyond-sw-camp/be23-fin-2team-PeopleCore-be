package com.peoplecore.controller;

import com.peoplecore.dto.SearchResponse;
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
            @RequestHeader("X-User-Company") String companyId
    ) {
        SearchResponse response = searchService.search(keyword, type, companyId, page, size);
        return ResponseEntity.ok(response);
    }
}
