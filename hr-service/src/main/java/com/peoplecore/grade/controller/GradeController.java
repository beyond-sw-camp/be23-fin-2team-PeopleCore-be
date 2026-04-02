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

@RestController
@RequestMapping("/api/grades")
public class GradeController {
    private final GradeService gradeService;

    public GradeController(GradeService gradeService) {
        this.gradeService = gradeService;
    }

    @GetMapping
    public ResponseEntity<List<GradeResponse>> getGrades() {
        return ResponseEntity.ok(gradeService.getGrades());
    }

    @PostMapping
    public ResponseEntity<GradeResponse> createGrade(
            @RequestBody @Valid GradeCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(gradeService.createGrade(request));
    }

    @PatchMapping("/{gradeId}")
    public ResponseEntity<GradeResponse> updateGrade(
            @PathVariable Long gradeId,
            @RequestBody @Valid GradeUpdateRequest request) {
        return ResponseEntity.ok(gradeService.updateGrade(gradeId, request));
    }

    @DeleteMapping("/{gradeId}")
    public ResponseEntity<Void> deleteGrade(@PathVariable Long gradeId) {
        gradeService.deleteGrade(gradeId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/order")
    public ResponseEntity<Void> updateOrder(
            @RequestBody GradeOrderRequest request) {
        gradeService.updateOrder(request);
        return ResponseEntity.ok().build();
    }
}
