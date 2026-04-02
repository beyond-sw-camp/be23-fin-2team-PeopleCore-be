package com.peoplecore.grade.service;

import com.peoplecore.grade.domain.Grade;
import com.peoplecore.grade.dto.GradeCreateRequest;
import com.peoplecore.grade.dto.GradeOrderRequest;
import com.peoplecore.grade.dto.GradeResponse;
import com.peoplecore.grade.dto.GradeUpdateRequest;
import com.peoplecore.grade.repository.GradeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class GradeService {
    private final GradeRepository gradeRepository;

    public GradeService(GradeRepository gradeRepository) {
        this.gradeRepository = gradeRepository;
    }

    public List<GradeResponse> getGrades() {
        return gradeRepository.findAllByOrderByGradeOrderAsc()
                .stream()
                .map(GradeResponse::from)
                .toList();
    }

    public GradeResponse createGrade(GradeCreateRequest request) {
        if (gradeRepository.existsByGradeName(request.getGradeName())) {
            throw new IllegalArgumentException("이미 존재하는 직급명입니다.");
        }

        int nextOrder = (int) gradeRepository.count() + 1;
        // 코드 자동 생성 (001, 002, ...)
        String gradeCode = String.format("%03d", nextOrder);

        Grade grade = Grade.builder()
                .gradeName(request.getGradeName())
                .gradeCode(gradeCode)
                .gradeOrder(nextOrder)
                .build();

        return GradeResponse.from(gradeRepository.save(grade));
    }

    public GradeResponse updateGrade(Long gradeId, GradeUpdateRequest request) {
        Grade grade = gradeRepository.findById(gradeId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 직급입니다."));

        grade.update(request.getGradeName(), grade.getGradeCode());
        return GradeResponse.from(grade);
    }

    public void deleteGrade(Long gradeId) {
        Grade grade = gradeRepository.findById(gradeId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 직급입니다."));
        gradeRepository.delete(grade);
    }

    public void updateOrder(GradeOrderRequest request) {
        List<Long> gradeIds = request.getGradeIds();
        for (int i = 0; i < gradeIds.size(); i++) {
            Grade grade = gradeRepository.findById(gradeIds.get(i))
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 직급입니다."));
            grade.updateOrder(i + 1);
        }
    }
}
