package com.peoplecore.grade.service;

import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.grade.domain.Grade;
import com.peoplecore.grade.dto.GradeCreateRequest;
import com.peoplecore.grade.dto.GradeOrderRequest;
import com.peoplecore.grade.dto.GradeResponse;
import com.peoplecore.grade.dto.GradeUpdateRequest;
import com.peoplecore.grade.repository.GradeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class GradeService {
    private final GradeRepository gradeRepository;
    private final EmployeeRepository employeeRepository;

    public GradeService(GradeRepository gradeRepository, EmployeeRepository employeeRepository) {
        this.gradeRepository = gradeRepository;
        this.employeeRepository = employeeRepository;
    }

    public List<GradeResponse> getGrades(UUID companyId) {
        return gradeRepository.findAllByCompanyIdOrderByGradeOrderAsc(companyId)
                .stream()
                .map(GradeResponse::from)
                .toList();
    }

    public GradeResponse createGrade(UUID companyId, GradeCreateRequest request) {
        if (gradeRepository.existsByGradeNameAndCompanyId(request.getGradeName(), companyId)) {
            throw new IllegalArgumentException("이미 존재하는 직급명입니다.");
        }

        int nextOrder = (int) gradeRepository.countByCompanyId(companyId) + 1;
        String gradeCode = String.format("%03d", nextOrder);

        Grade grade = Grade.builder()
                .companyId(companyId)
                .gradeName(request.getGradeName())
                .gradeCode(gradeCode)
                .gradeOrder(nextOrder)
                .build();

        return GradeResponse.from(gradeRepository.save(grade));
    }

    public GradeResponse updateGrade(UUID companyId, Long gradeId, GradeUpdateRequest request) {
        Grade grade = gradeRepository.findById(gradeId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 직급입니다."));

        if (!grade.getCompanyId().equals(companyId)) {
            throw new IllegalArgumentException("접근 권한이 없습니다.");
        }

        grade.update(request.getGradeName(), grade.getGradeCode());
        return GradeResponse.from(grade);
    }

    public void deleteGrade(UUID companyId, Long gradeId) {
        Grade grade = gradeRepository.findById(gradeId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 직급입니다."));

        if (!grade.getCompanyId().equals(companyId)) {
            throw new IllegalArgumentException("접근 권한이 없습니다.");
        }

        if (employeeRepository.existsByGrade(grade)) {
            throw new IllegalStateException("해당 직급을 사용 중인 직원이 있어 삭제할 수 없습니다.");
        }

        gradeRepository.delete(grade);
    }

    public void updateOrder(UUID companyId, GradeOrderRequest request) {
        List<Long> gradeIds = request.getGradeIds();
        for (int i = 0; i < gradeIds.size(); i++) {
            Grade grade = gradeRepository.findById(gradeIds.get(i))
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 직급입니다."));

            if (!grade.getCompanyId().equals(companyId)) {
                throw new IllegalArgumentException("접근 권한이 없습니다.");
            }

            grade.updateOrder(i + 1);
        }
    }
}
