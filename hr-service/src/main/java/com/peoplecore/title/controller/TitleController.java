package com.peoplecore.title.controller;

import com.peoplecore.title.dto.TitleCreateRequest;
import com.peoplecore.title.dto.TitleOrderRequest;
import com.peoplecore.title.dto.TitleResponse;
import com.peoplecore.title.dto.TitleUpdateRequest;
import com.peoplecore.title.service.TitleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/titles")
public class TitleController {
    private final TitleService titleService;

    public TitleController(TitleService titleService) {
        this.titleService = titleService;
    }

    @GetMapping
    public ResponseEntity<List<TitleResponse>> getTitles(
            @RequestHeader("X-User-Company") String companyId) {
        return ResponseEntity.ok(titleService.getTitles(UUID.fromString(companyId)));
    }

    @PostMapping
    public ResponseEntity<TitleResponse> createTitle(
            @RequestHeader("X-User-Company") String companyId,
            @RequestBody @Valid TitleCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(titleService.createTitle(UUID.fromString(companyId), request));
    }

    @PatchMapping("/{titleId}")
    public ResponseEntity<TitleResponse> updateTitle(
            @RequestHeader("X-User-Company") String companyId,
            @PathVariable Long titleId,
            @RequestBody @Valid TitleUpdateRequest request) {
        return ResponseEntity.ok(titleService.updateTitle(UUID.fromString(companyId), titleId, request));
    }

    @DeleteMapping("/{titleId}")
    public ResponseEntity<Void> deleteTitle(
            @RequestHeader("X-User-Company") String companyId,
            @PathVariable Long titleId) {
        titleService.deleteTitle(UUID.fromString(companyId), titleId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/order")
    public ResponseEntity<Void> updateOrder(
            @RequestHeader("X-User-Company") String companyId,
            @RequestBody TitleOrderRequest request) {
        titleService.updateOrder(UUID.fromString(companyId), request);
        return ResponseEntity.ok().build();
    }
}
