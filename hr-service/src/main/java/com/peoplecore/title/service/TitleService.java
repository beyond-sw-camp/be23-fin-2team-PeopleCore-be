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
import com.peoplecore.employee.repository.EmployeeRepository;
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
    private final EmployeeRepository employeeRepository;

    public TitleService(TitleRepository titleRepository, DepartmentRepository departmentRepository, EmployeeRepository employeeRepository) {
        this.titleRepository = titleRepository;
        this.departmentRepository = departmentRepository;
        this.employeeRepository = employeeRepository;
    }

    public List<TitleResponse> getTitles(UUID companyId) {
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

        String titleCode = titleRepository.findTopByCompanyIdOrderByTitleCodeDesc(companyId)
                .map(t -> String.format("%03d", Integer.parseInt(t.getTitleCode()) + 1))
                .orElse("001");

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

        if (employeeRepository.existsByTitle(title)) {
            throw new IllegalStateException("해당 직책을 사용 중인 직원이 있어 삭제할 수 없습니다.");
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
