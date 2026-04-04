package com.peoplecore.grade.controller;

import com.peoplecore.grade.dto.GradeCreateRequest;
import com.peoplecore.grade.dto.GradeOrderRequest;
import com.peoplecore.grade.dto.GradeResponse;
import com.peoplecore.grade.dto.GradeUpdateRequest;
import com.peoplecore.grade.service.GradeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/grades")
public class GradeController {
    private final GradeService gradeService;

    public GradeController(GradeService gradeService) {
        this.gradeService = gradeService;
    }

    @GetMapping
    public ResponseEntity<List<GradeResponse>> getGrades(
            @RequestHeader("X-User-Company") String companyId) {
        return ResponseEntity.ok(gradeService.getGrades(UUID.fromString(companyId)));
    }

    @PostMapping
    public ResponseEntity<GradeResponse> createGrade(
            @RequestHeader("X-User-Company") String companyId,
            @RequestBody @Valid GradeCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(gradeService.createGrade(UUID.fromString(companyId), request));
    }

    @PatchMapping("/{gradeId}")
    public ResponseEntity<GradeResponse> updateGrade(
            @RequestHeader("X-User-Company") String companyId,
            @PathVariable Long gradeId,
            @RequestBody @Valid GradeUpdateRequest request) {
        return ResponseEntity.ok(gradeService.updateGrade(UUID.fromString(companyId), gradeId, request));
    }

    @DeleteMapping("/{gradeId}")
    public ResponseEntity<Void> deleteGrade(
            @RequestHeader("X-User-Company") String companyId,
            @PathVariable Long gradeId) {
        gradeService.deleteGrade(UUID.fromString(companyId), gradeId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/order")
    public ResponseEntity<Void> updateOrder(
            @RequestHeader("X-User-Company") String companyId,
            @RequestBody GradeOrderRequest request) {
        gradeService.updateOrder(UUID.fromString(companyId), request);
        return ResponseEntity.ok().build();
    }
}
