package com.peoplecore.evaluation.service;

import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.evaluation.domain.EvalSeasonStatus;
import com.peoplecore.evaluation.domain.EvaluatorRoleConfig;
import com.peoplecore.evaluation.domain.EvaluatorRoleDeptAssignment;
import com.peoplecore.evaluation.domain.EvaluatorRoleMode;
import com.peoplecore.evaluation.dto.DeptCandidateDto;
import com.peoplecore.evaluation.dto.DeptOverrideDto;
import com.peoplecore.evaluation.dto.DeptResolutionDto;
import com.peoplecore.evaluation.dto.EvaluatorRoleConfigResponse;
import com.peoplecore.evaluation.dto.EvaluatorRolePreviewResponse;
import com.peoplecore.evaluation.dto.EvaluatorRoleUpdateRequest;
import com.peoplecore.evaluation.dto.MyEvaluatorRoleResponse;
import com.peoplecore.evaluation.repository.EvaluatorRoleConfigRepository;
import com.peoplecore.evaluation.repository.EvaluatorRoleDeptAssignmentRepository;
import com.peoplecore.evaluation.repository.SeasonRepository;
import com.peoplecore.exception.BusinessException;
import com.peoplecore.grade.domain.Grade;
import com.peoplecore.grade.repository.GradeRepository;
import com.peoplecore.title.domain.Title;
import com.peoplecore.title.repository.TitleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// 평가자 판정 수정: OPEN 시즌 있을 시 저장 차단.
@Service
@Transactional
@Slf4j
public class EvaluatorRoleService {

    // 한 번도 저장 안 된 회사 기본
    private static final EvaluatorRoleMode DEFAULT_MODE = EvaluatorRoleMode.GRADE;

    private final EvaluatorRoleConfigRepository configRepository;
    private final EvaluatorRoleDeptAssignmentRepository assignmentRepository;
    private final GradeRepository gradeRepository;
    private final TitleRepository titleRepository;
    private final SeasonRepository seasonRepository;
    private final EmployeeRepository employeeRepository;

    public EvaluatorRoleService(EvaluatorRoleConfigRepository configRepository,
                                EvaluatorRoleDeptAssignmentRepository assignmentRepository,
                                GradeRepository gradeRepository,
                                TitleRepository titleRepository,
                                SeasonRepository seasonRepository,
                                EmployeeRepository employeeRepository) {
        this.configRepository = configRepository;
        this.assignmentRepository = assignmentRepository;
        this.gradeRepository = gradeRepository;
        this.titleRepository = titleRepository;
        this.seasonRepository = seasonRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional(readOnly = true)// 회사의 현재 평가자 설정 + 부서별 배정 조회. row 없으면 기본값.
    public EvaluatorRoleConfigResponse getConfig(UUID companyId) {
        Optional<EvaluatorRoleConfig> opt = configRepository.findByCompanyId(companyId);

        // 설정 row 없는 회사 — 기본값 응답
        if (opt.isEmpty()) {
            return EvaluatorRoleConfigResponse.builder()
                .mode(DEFAULT_MODE)
                .grantedTargetId(null)
                .overrides(new ArrayList<>())
                .build();
        }
        EvaluatorRoleConfig config = opt.get();

        // 저장된 부서별 배정을 overrides 로 변환
        List<EvaluatorRoleDeptAssignment> assigns = assignmentRepository.findByConfig_Id(config.getId());
        List<DeptOverrideDto> overrides = new ArrayList<>();
        for (EvaluatorRoleDeptAssignment a : assigns) {
            overrides.add(DeptOverrideDto.builder()
                .deptId(a.getDept().getDeptId())
                .empId(a.getEmployee().getEmpId())
                .build());
        }

        return EvaluatorRoleConfigResponse.builder()
            .mode(config.deriveMode())
            .grantedTargetId(config.getGrantedTargetId())
            .overrides(overrides)
            .build();
    }

    // 저장/갱신. OPEN 시즌 차단 + config upsert + 부서별 배정 원자 교체.
    public EvaluatorRoleConfigResponse updateConfig(UUID companyId, EvaluatorRoleUpdateRequest request) {

        //  진행 중(OPEN) 시즌 있으면 평가자 변경 금지
        boolean hasOpenSeason = seasonRepository.existsByCompany_CompanyIdAndStatus(
            companyId, EvalSeasonStatus.OPEN);
        if (hasOpenSeason) {
            throw new BusinessException(
                "진행 중인 평가 시즌이 있어 평가자를 변경할 수 없습니다.",
                HttpStatus.CONFLICT);
        }

        // 기존 row 없으면 신규 엔티티 준비
        EvaluatorRoleConfig config = configRepository.findByCompanyId(companyId)
            .orElseGet(() -> EvaluatorRoleConfig.builder()
                .companyId(companyId)
                .build());

        // 모드에 맞는 id 로드 + 회사 소속 확인 후 엔티티에 set
        if (request.getMode() == EvaluatorRoleMode.GRADE) {
            Grade grade = loadGradeOfCompany(request.getGrantedTargetId(), companyId);
            config.setGradeTarget(grade);
        } else {
            Title title = loadTitleOfCompany(request.getGrantedTargetId(), companyId);
            config.setTitleTarget(title);
        }

        // save. 엔티티 XOR 검증 자동 실행
        EvaluatorRoleConfig savedConfig = configRepository.save(config);

        // 매칭되는 사원을 부서별로 그룹핑
        List<Employee> employees = fetchMatchingEmployees(
            companyId, request.getMode(), request.getGrantedTargetId());
        Map<Long, List<Employee>> byDept = new LinkedHashMap<>();
        for (Employee e : employees) {
            Long deptId = e.getDept().getDeptId();
            byDept.computeIfAbsent(deptId, k -> new ArrayList<>()).add(e);
        }

        // HR 선택값을 빠른 조회용 Map 으로 변환
        Map<Long, Long> overrideMap = new HashMap<>();
        if (request.getOverrides() != null) {
            for (DeptOverrideDto o : request.getOverrides()) {
                overrideMap.put(o.getDeptId(), o.getEmpId());
            }
        }

        // 부서별 최종 평가자 1명 결정 → 엔티티 리스트 준비
        List<EvaluatorRoleDeptAssignment> toSave = new ArrayList<>();
        for (Map.Entry<Long, List<Employee>> entry : byDept.entrySet()) {
            List<Employee> members = entry.getValue();
            Employee picked = resolvePicked(members, overrideMap, entry.getKey());
            toSave.add(EvaluatorRoleDeptAssignment.builder()
                .config(savedConfig)
                .dept(picked.getDept())
                .employee(picked)
                .build());
        }

        // 기존 배정 전부 삭제 → flush → 새 배정 일괄 insert (UNIQUE 충돌 방지)
        assignmentRepository.deleteByConfig_Id(savedConfig.getId());
        assignmentRepository.flush();
        assignmentRepository.saveAll(toSave);

        log.info("평가자 역할 갱신 companyId={}, mode={}, targetId={}, assignments={}",
            companyId, request.getMode(), request.getGrantedTargetId(), toSave.size());

        return getConfig(companyId);
    }

    // 현재 사용자의 평가자 여부를 DTO 로 감싸서 반환
    public MyEvaluatorRoleResponse me(UUID companyId, Long empId) {
        boolean result = isEvaluator(companyId, empId);
        return MyEvaluatorRoleResponse.builder()
            .evaluator(result)
            .build();
    }

    // empId 가 이 회사의 부서별 배정에 있으면 평가자.
    public boolean isEvaluator(UUID companyId, Long empId) {
        if (empId == null) return false;
        return assignmentRepository.existsByConfig_CompanyIdAndEmployee_EmpId(companyId, empId);
    }

    // mode+targetId 매칭 사원 조회 (preview / updateConfig 공용).
    private List<Employee> fetchMatchingEmployees(UUID companyId, EvaluatorRoleMode mode, Long targetId) {
        if (mode == EvaluatorRoleMode.GRADE) {
            return employeeRepository.findActiveByCompanyAndGrade(companyId, targetId);
        }
        return employeeRepository.findActiveByCompanyAndTitle(companyId, targetId);
    }

    // 한 부서에서 평가자 1명 선정. 1명이면 자동, 복수면 HR override 필수.
    private Employee resolvePicked(List<Employee> members, Map<Long, Long> overrideMap, Long deptId) {
        String deptName = members.get(0).getDept().getDeptName();

        // 1명이면 자동 배정
        if (members.size() == 1) {
            return members.get(0);
        }

        // 복수인데 HR 이 안 고르면 에러
        Long pickedEmpId = overrideMap.get(deptId);
        if (pickedEmpId == null) {
            throw new BusinessException(
                "부서 '" + deptName + "' 에 평가자가 여러 명이라 1명 지정이 필요합니다.",
                HttpStatus.BAD_REQUEST);
        }

        // 지정한 사원이 실제 매칭 후보 중 1명인지 검증
        for (Employee m : members) {
            if (m.getEmpId().equals(pickedEmpId)) return m;
        }
        throw new BusinessException(
            "부서 '" + deptName + "' 에 지정한 사원이 해당 직급/직책이 아닙니다.",
            HttpStatus.BAD_REQUEST);
    }

    // 선택한 mode+targetId 에 대한 부서별 매칭 결과 미리보기.
    @Transactional(readOnly = true)
    public EvaluatorRolePreviewResponse preview(UUID companyId, EvaluatorRoleMode mode, Long targetId) {
        // 대상 존재 + 소속 검증
        if (mode == EvaluatorRoleMode.GRADE) {
            loadGradeOfCompany(targetId, companyId);
        } else {
            loadTitleOfCompany(targetId, companyId);
        }

        // 매칭 사원 로드
        List<Employee> employees = fetchMatchingEmployees(companyId, mode, targetId);

        // 부서별 그룹핑 (순서 유지)
        Map<Long, List<Employee>> byDept = new LinkedHashMap<>();
        Map<Long, String> deptNames = new HashMap<>();
        for (Employee e : employees) {
            Long deptId = e.getDept().getDeptId();
            byDept.computeIfAbsent(deptId, k -> new ArrayList<>()).add(e);
            deptNames.putIfAbsent(deptId, e.getDept().getDeptName());
        }

        // DTO 변환
        List<DeptResolutionDto> depts = new ArrayList<>();
        for (Map.Entry<Long, List<Employee>> entry : byDept.entrySet()) {
            List<DeptCandidateDto> candidates = new ArrayList<>();
            for (Employee m : entry.getValue()) {
                candidates.add(DeptCandidateDto.builder()
                    .empId(m.getEmpId())
                    .empName(m.getEmpName())
                    .build());
            }
            depts.add(DeptResolutionDto.builder()
                .deptId(entry.getKey())
                .deptName(deptNames.get(entry.getKey()))
                .candidates(candidates)
                .conflict(candidates.size() >= 2)
                .build());
        }

        return EvaluatorRolePreviewResponse.builder()
            .mode(mode)
            .grantedTargetId(targetId)
            .depts(depts)
            .build();
    }

    // 직급 로드 + 회사 소속 확인.
    private Grade loadGradeOfCompany(Long gradeId, UUID companyId) {
        Grade grade = gradeRepository.findById(gradeId)
            .orElseThrow(() -> new BusinessException(
                "선택한 직급이 존재하지 않습니다.", HttpStatus.BAD_REQUEST));
        if (!grade.getCompanyId().equals(companyId)) {
            throw new BusinessException(
                "다른 회사의 직급은 지정할 수 없습니다.", HttpStatus.BAD_REQUEST);
        }
        return grade;
    }

    // 직책 로드 + 회사 소속 확인.
    private Title loadTitleOfCompany(Long titleId, UUID companyId) {
        Title title = titleRepository.findById(titleId)
            .orElseThrow(() -> new BusinessException(
                "선택한 직책이 존재하지 않습니다.", HttpStatus.BAD_REQUEST));
        if (!title.getCompanyId().equals(companyId)) {
            throw new BusinessException(
                "다른 회사의 직책은 지정할 수 없습니다.", HttpStatus.BAD_REQUEST);
        }
        return title;
    }
}
