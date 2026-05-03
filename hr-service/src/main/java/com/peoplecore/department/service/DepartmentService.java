package com.peoplecore.department.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplecore.company.domain.Company;
import com.peoplecore.event.DeptUpdatedEvent;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.department.domain.Department;
import com.peoplecore.department.domain.UseStatus;
import com.peoplecore.department.dto.DepartmentCreateRequest;
import com.peoplecore.department.dto.DepartmentDetailResponse;
import com.peoplecore.department.dto.DepartmentReorderRequest;
import com.peoplecore.department.dto.DepartmentResponse;
import com.peoplecore.department.dto.DepartmentUpdateRequest;
import com.peoplecore.department.dto.OrgChartMemberDto;
import com.peoplecore.department.dto.OrgChartResponse;
import com.peoplecore.employee.domain.EmpStatus;
import com.peoplecore.department.repository.DepartmentRepository;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;
    /* 부서 변경 이벤트 발생 시 카프카 메세지 발생*/
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private static final String TOPIC_DEPT_UPDATED = "hr-dept-updated";

    @Autowired
    public DepartmentService(DepartmentRepository departmentRepository, EmployeeRepository employeeRepository, KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.departmentRepository = departmentRepository;
        this.employeeRepository = employeeRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }


    /**
     * 조직도 트리 조회 — 최상위 부서부터 재귀적으로 하위 부서를 포함
     */
    public List<DepartmentResponse> getOrgTree(UUID companyId) {
        List<Department> allDepts = departmentRepository
                .findByCompany_CompanyIdAndIsUseOrderBySortOrderAscDeptIdAsc(companyId, UseStatus.Y);

        Map<Long, Long> memberCountMap = getMemberCountMap(companyId);

        List<Department> roots = allDepts.stream()
                .filter(d -> d.getParentDeptId() == null)
                .toList();

        return roots.stream()
                .map(root -> buildTree(root, allDepts, memberCountMap))
                .toList();
    }

    public List<DepartmentResponse> getAllDepartments(UUID companyId) {
        Map<Long, Long> memberCountMap = getMemberCountMap(companyId);

        return departmentRepository
                .findByCompany_CompanyIdAndIsUseOrderBySortOrderAscDeptIdAsc(companyId, UseStatus.Y)
                .stream()
                .map(dept -> DepartmentResponse.from(
                        dept,
                        memberCountMap.getOrDefault(dept.getDeptId(), 0L)
                ))
                .toList();
    }

    public DepartmentResponse getDepartment(UUID companyId, Long deptId) {
        Department dept = findDepartmentOrThrow(companyId, deptId);
        long memberCount = countMembers(companyId, deptId);
        return DepartmentResponse.from(dept, memberCount);
    }

    /**
     * 부서 상세 조회 — 직책 보유자, 재직 인원 수, 하위 부서 수 포함
     */
    @Transactional(readOnly = true)
    public DepartmentDetailResponse getDepartmentDetail(UUID companyId, Long deptId) {
        Department dept = findDepartmentOrThrow(companyId, deptId);

        List<Employee> titleHolders = employeeRepository
                .findTitleHoldersByDeptId(companyId, deptId);

        long activeCount = countMembers(companyId, deptId);

        int childDeptCount = departmentRepository
                .findByCompany_CompanyIdAndParentDeptIdAndIsUse(companyId, deptId, UseStatus.Y)
                .size();

        return DepartmentDetailResponse.of(dept, titleHolders, activeCount, childDeptCount);
    }

    /**
     * 조직도 전용 조회 — 부서 트리 + 소속 사원 (전 사원 접근 가능)
     */
    @Transactional(readOnly = true)
    public List<OrgChartResponse> getOrgChartWithMembers(UUID companyId) {
        List<Department> allDepts = departmentRepository
                .findByCompany_CompanyIdAndIsUseOrderBySortOrderAscDeptIdAsc(companyId, UseStatus.Y);

        List<Employee> allEmployees = employeeRepository.findAll().stream()
                .filter(e -> e.getCompany().getCompanyId().equals(companyId))
                .filter(e -> e.getEmpStatus() != EmpStatus.RESIGNED)
                .toList();

        Map<Long, List<OrgChartMemberDto>> membersByDept = allEmployees.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getDept().getDeptId(),
                        Collectors.mapping(OrgChartMemberDto::from, Collectors.toList())
                ));

        List<Department> roots = allDepts.stream()
                .filter(d -> d.getParentDeptId() == null)
                .toList();

        return roots.stream()
                .map(root -> buildOrgChartTree(root, allDepts, membersByDept))
                .toList();
    }

    private OrgChartResponse buildOrgChartTree(
            Department dept,
            List<Department> allDepts,
            Map<Long, List<OrgChartMemberDto>> membersByDept
    ) {
        List<OrgChartResponse> children = allDepts.stream()
                .filter(d -> dept.getDeptId().equals(d.getParentDeptId()))
                .map(child -> buildOrgChartTree(child, allDepts, membersByDept))
                .toList();

        List<OrgChartMemberDto> members = membersByDept.getOrDefault(dept.getDeptId(), List.of());

        return OrgChartResponse.of(dept, members, children);
    }

    // ========================= 생성 =========================

    @Transactional
    public DepartmentResponse createDepartment(UUID companyId, DepartmentCreateRequest request) {

        if (departmentRepository.existsByCompany_CompanyIdAndDeptNameAndIsUse(
                companyId, request.getDeptName(), UseStatus.Y)) {
            throw new CustomException(ErrorCode.DEPARTMENT_NAME_DUPLICATE);
        }

        if (departmentRepository.existsByCompany_CompanyIdAndDeptCodeAndIsUse(
                companyId, request.getDeptCode(), UseStatus.Y)) {
            throw new CustomException(ErrorCode.DEPARTMENT_CODE_DUPLICATE);
        }

        if (request.getParentDeptId() != null) {
            findDepartmentOrThrow(companyId, request.getParentDeptId());
        }

        Company company = Company.builder()
                .companyId(companyId)
                .build();

        int nextSortOrder = nextSortOrderForParent(companyId, request.getParentDeptId());

        Department dept = Department.builder()
                .company(company)
                .parentDeptId(request.getParentDeptId())
                .deptName(request.getDeptName())
                .deptCode(request.getDeptCode().toUpperCase())
                .sortOrder(nextSortOrder)
                .build();

        Department saved = departmentRepository.save(dept);
        return DepartmentResponse.from(saved, 0);
    }

    private int nextSortOrderForParent(UUID companyId, Long parentDeptId) {
        return departmentRepository
                .findByCompany_CompanyIdAndIsUseOrderBySortOrderAscDeptIdAsc(companyId, UseStatus.Y)
                .stream()
                .filter(d -> java.util.Objects.equals(d.getParentDeptId(), parentDeptId))
                .map(Department::getSortOrder)
                .filter(java.util.Objects::nonNull)
                .max(Integer::compareTo)
                .map(max -> max + 1)
                .orElse(1);
    }

    // ========================= 수정 =========================

    @Transactional
    public DepartmentResponse updateDepartment(UUID companyId, Long deptId, DepartmentUpdateRequest request) {

        Department dept = findDepartmentOrThrow(companyId, deptId);

        if (request.getDeptName() != null && !request.getDeptName().equals(dept.getDeptName())) {

            if (departmentRepository.existsByCompany_CompanyIdAndDeptNameAndIsUse(
                    companyId, request.getDeptName(), UseStatus.Y)) {
                throw new CustomException(ErrorCode.DEPARTMENT_NAME_DUPLICATE);
            }

            dept.updateName(request.getDeptName());
        }

        if (request.getDeptCode() != null && !request.getDeptCode().equals(dept.getDeptCode())) {

            if (departmentRepository.existsByCompany_CompanyIdAndDeptCodeAndIsUse(
                    companyId, request.getDeptCode().toUpperCase(), UseStatus.Y)) {
                throw new CustomException(ErrorCode.DEPARTMENT_CODE_DUPLICATE);
            }

            dept.updateCode(request.getDeptCode().toUpperCase());
        }

        if (request.getParentDeptId() != null) {

            if (request.getParentDeptId().equals(deptId)) {
                throw new CustomException(ErrorCode.DEPARTMENT_CIRCULAR_REFERENCE);
            }

            findDepartmentOrThrow(companyId, request.getParentDeptId());
            validateNoCircularReference(companyId, deptId, request.getParentDeptId());

            dept.updateParent(request.getParentDeptId());
        }

//        부서 정보 변경 시 kafka 이벤트 발행
        publishDeptUpdatedEvent(deptId);

        long memberCount = countMembers(companyId, deptId);
        return DepartmentResponse.from(dept, memberCount);
    }

    // ========================= 순서/위치 일괄 변경 =========================

    /**
     * 조직도 드래그&드롭 저장 — parentDeptId, sortOrder를 한 번의 트랜잭션으로 일괄 반영.
     * 모든 항목 검증 후 적용하므로 일부 실패 시 전체 롤백된다.
     */
    @Transactional
    public void reorderDepartments(UUID companyId, DepartmentReorderRequest request) {
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            return;
        }

        List<Department> allDepts = departmentRepository
                .findByCompany_CompanyIdAndIsUseOrderBySortOrderAscDeptIdAsc(companyId, UseStatus.Y);

        Map<Long, Department> deptById = allDepts.stream()
                .collect(Collectors.toMap(Department::getDeptId, d -> d));

        // 적용 후 parent 맵 — 사이클 검증용 (제출된 변경을 모두 반영했다고 가정한 그래프)
        Map<Long, Long> nextParentMap = allDepts.stream()
                .collect(Collectors.toMap(
                        Department::getDeptId,
                        d -> d.getParentDeptId() != null ? d.getParentDeptId() : -1L
                ));

        for (DepartmentReorderRequest.Item item : request.getItems()) {
            if (item.getDeptId() == null) {
                throw new CustomException(ErrorCode.DEPARTMENT_NOT_FOUND);
            }
            Department dept = deptById.get(item.getDeptId());
            if (dept == null) {
                throw new CustomException(ErrorCode.DEPARTMENT_NOT_FOUND);
            }
            Long newParentId = item.getParentDeptId();
            if (newParentId != null) {
                if (newParentId.equals(item.getDeptId())) {
                    throw new CustomException(ErrorCode.DEPARTMENT_CIRCULAR_REFERENCE);
                }
                if (!deptById.containsKey(newParentId)) {
                    throw new CustomException(ErrorCode.DEPARTMENT_NOT_FOUND);
                }
            }
            nextParentMap.put(item.getDeptId(), newParentId != null ? newParentId : -1L);
        }

        // 사이클 검증 — 적용 후 그래프에서 deptId → 루트 경로상 자기 자신이 다시 나오면 안 됨
        for (DepartmentReorderRequest.Item item : request.getItems()) {
            Long current = nextParentMap.get(item.getDeptId());
            int guard = nextParentMap.size() + 1;
            while (current != null && current != -1L && guard-- > 0) {
                if (current.equals(item.getDeptId())) {
                    throw new CustomException(ErrorCode.DEPARTMENT_CIRCULAR_REFERENCE);
                }
                current = nextParentMap.get(current);
            }
            if (guard < 0) {
                throw new CustomException(ErrorCode.DEPARTMENT_CIRCULAR_REFERENCE);
            }
        }

        // 적용
        for (DepartmentReorderRequest.Item item : request.getItems()) {
            Department dept = deptById.get(item.getDeptId());
            int sort = item.getSortOrder() != null ? item.getSortOrder() : 0;
            dept.updatePositionAndOrder(item.getParentDeptId(), sort);
        }

        // 트리 캐시 갱신용 이벤트
        for (DepartmentReorderRequest.Item item : request.getItems()) {
            publishDeptUpdatedEvent(item.getDeptId());
        }
    }

    // ========================= 삭제 =========================

    public void deleteDepartment(UUID companyId, Long deptId) {

        Department dept = findDepartmentOrThrow(companyId, deptId);

        long memberCount = countMembers(companyId, deptId);
        if (memberCount > 0) {
            throw new CustomException(ErrorCode.DEPARTMENT_HAS_MEMBERS);
        }

        if (departmentRepository.existsByParentDeptIdAndIsUse(deptId, UseStatus.Y)) {
            throw new CustomException(ErrorCode.DEPARTMENT_HAS_CHILDREN);
        }

        dept.deactivate();

        /* 부서 삭제 또는 비활성화된 부서가 캐시에 남아있을 수 도 있으니 캐시 삭제 메세지 발행 */
        publishDeptUpdatedEvent(deptId);
    }

    /* 부서 변경 이벤트 kafka로 발행
     * 실패해도 다른 로직에 영향을 주지 않도록 try-catch에서예외처리 x  */
    private void publishDeptUpdatedEvent(Long deptId) {
        try {
            String message = objectMapper.writeValueAsString(new DeptUpdatedEvent(deptId));
            kafkaTemplate.send(TOPIC_DEPT_UPDATED, String.valueOf(deptId), message);
            log.info("부서 변경 이벤트 발행 완료 topic = {}, deptId = {} ", TOPIC_DEPT_UPDATED, deptId);
        } catch (JsonProcessingException e) {
            log.error("부서 변경 이벤트 직렬화 실패 deptId = {}, error = {} ", deptId, e.getMessage());
        } catch (Exception e) {
            log.error("부서 변경 이벤트 발행 실패 deptId = {} , error = {} ", deptId, e.getMessage());
        }
    }


    // ========================= 내부 메서드 =========================


    private Department findDepartmentOrThrow(UUID companyId, Long deptId) {
        return departmentRepository
                .findByDeptIdAndCompany_CompanyId(deptId, companyId)
                .filter(d -> d.getIsUse() == UseStatus.Y)
                .orElseThrow(() -> new CustomException(ErrorCode.DEPARTMENT_NOT_FOUND));
    }

    private void validateNoCircularReference(UUID companyId, Long deptId, Long newParentId) {

        List<Department> allDepts = departmentRepository
                .findByCompany_CompanyIdAndIsUseOrderBySortOrderAscDeptIdAsc(companyId, UseStatus.Y);

        Map<Long, Long> parentMap = allDepts.stream()
                .collect(Collectors.toMap(
                        Department::getDeptId,
                        d -> d.getParentDeptId() != null ? d.getParentDeptId() : -1L
                ));

        Long current = newParentId;

        while (current != null && current != -1L) {
            if (current.equals(deptId)) {
                throw new CustomException(ErrorCode.DEPARTMENT_CIRCULAR_REFERENCE);
            }
            current = parentMap.get(current);
        }
    }

    private long countMembers(UUID companyId, Long deptId) {
        return employeeRepository
                .countByCompany_CompanyIdAndDept_DeptId(companyId, deptId);
    }

    private Map<Long, Long> getMemberCountMap(UUID companyId) {
        return employeeRepository
                .countByCompanyIdGroupByDeptId(companyId)
                .stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]
                ));
    }

    private DepartmentResponse buildTree(
            Department dept,
            List<Department> allDepts,
            Map<Long, Long> memberCountMap
    ) {

        List<DepartmentResponse> children = allDepts.stream()
                .filter(d -> dept.getDeptId().equals(d.getParentDeptId()))
                .map(child -> buildTree(child, allDepts, memberCountMap))
                .toList();

        long memberCount = memberCountMap.getOrDefault(dept.getDeptId(), 0L);

        return DepartmentResponse.withChildren(dept, memberCount, children);
    }

    //superAdmin 계정 생성시 초기값
    public void initDefault(Company company) {
        departmentRepository.save(
            Department.builder()
                    .company(company)
                    .deptName("미배정")
                    .deptCode("DEFAULT")
                    .build()
        );
    }

}
