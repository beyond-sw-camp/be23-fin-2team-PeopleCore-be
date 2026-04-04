package com.peoplecore.title.service;

import com.peoplecore.company.domain.Company;
import com.peoplecore.department.domain.Department;
import com.peoplecore.department.domain.UseStatus;
import com.peoplecore.department.repository.DepartmentRepository;
import com.peoplecore.title.domain.Title;
import com.peoplecore.title.dto.DepartmentSimpleResponse;
import com.peoplecore.title.dto.TitleCreateRequest;
import com.peoplecore.title.dto.TitleResponse;
import com.peoplecore.title.dto.TitleUpdateRequest;
import com.peoplecore.title.repository.TitleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class TitleService {
    private final TitleRepository titleRepository;
    private final DepartmentRepository departmentRepository;

    public TitleService(TitleRepository titleRepository, DepartmentRepository departmentRepository) {
        this.titleRepository = titleRepository;
        this.departmentRepository = departmentRepository;
    }

    public List<TitleResponse> getTitles(UUID companyId) {
        // 부서 목록 한 번만 조회해서 Map으로 변환 (N+1 방지)
        Map<Long, String> deptMap = departmentRepository
                .findByCompany_CompanyIdAndIsUseOrderByDeptNameAsc(companyId, UseStatus.Y)
                .stream()
                .collect(Collectors.toMap(Department::getDeptId, Department::getDeptName));

        return titleRepository.findAllByCompanyId(companyId)
                .stream()
                .map(title -> {
                    String deptName = title.getDeptId() == null
                            ? "전사 공통"
                            : deptMap.getOrDefault(title.getDeptId(), "전사 공통");
                    return TitleResponse.from(title, deptName);
                })
                .toList();
    }

    public List<DepartmentSimpleResponse> getDepartments(UUID companyId) {
        return departmentRepository
                .findByCompany_CompanyIdAndIsUseOrderByDeptNameAsc(companyId, UseStatus.Y)
                .stream()
                .map(dept -> new DepartmentSimpleResponse(dept.getDeptId(), dept.getDeptName()))
                .toList();
    }

    public TitleResponse createTitle(UUID companyId, TitleCreateRequest request) {
        if (titleRepository.existsByTitleNameAndCompanyIdAndDeptId(
                request.getTitleName(), companyId, request.getDeptId())) {
            throw new IllegalArgumentException("이미 존재하는 직책명입니다.");
        }

        String titleCode = String.format("%03d", titleRepository.countByCompanyId(companyId) + 1);

        Title title = Title.builder()
                .companyId(companyId)
                .deptId(request.getDeptId())
                .titleName(request.getTitleName())
                .titleCode(titleCode)
                .build();

        Title saved = titleRepository.save(title);

        String deptName = saved.getDeptId() == null
                ? "전사 공통"
                : departmentRepository.findById(saved.getDeptId())
                .map(Department::getDeptName)
                .orElse("전사 공통");

        return TitleResponse.from(saved, deptName);
    }

    public TitleResponse updateTitle(UUID companyId, Long titleId, TitleUpdateRequest request) {
        Title title = titleRepository.findById(titleId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 직책입니다."));

        if (!title.getCompanyId().equals(companyId)) {
            throw new IllegalArgumentException("접근 권한이 없습니다.");
        }

        // 자기 자신을 제외하고 같은 직책명 + 같은 부서가 있으면 중복
        if (titleRepository.existsByTitleNameAndCompanyIdAndDeptIdAndTitleIdNot(
                request.getTitleName(), companyId, request.getDeptId(), titleId)) {
            throw new IllegalArgumentException("이미 존재하는 직책명입니다.");
        }

        title.update(request.getTitleName(), request.getDeptId());

        String deptName = title.getDeptId() == null
                ? "전사 공통"
                : departmentRepository.findById(title.getDeptId())
                .map(Department::getDeptName)
                .orElse("전사 공통");

        return TitleResponse.from(title, deptName);
    }

    public void deleteTitle(UUID companyId, Long titleId) {
        Title title = titleRepository.findById(titleId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 직책입니다."));

        if (!title.getCompanyId().equals(companyId)) {
            throw new IllegalArgumentException("접근 권한이 없습니다.");
        }

        titleRepository.delete(title);
    }


    //superAdmin 계정 생성시 초기값
    public void initDefault(Company company) {
        titleRepository.save(
            Title.builder()
                    .companyId(company.getCompanyId())
                    .titleName("미배정")
                    .titleCode("DEFAULT")
                    .build()
        );
    }

}
