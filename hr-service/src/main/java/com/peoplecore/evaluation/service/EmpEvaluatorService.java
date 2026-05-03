package com.peoplecore.evaluation.service;

import com.peoplecore.department.domain.Department;
import com.peoplecore.department.domain.UseStatus;
import com.peoplecore.department.repository.DepartmentRepository;
import com.peoplecore.employee.domain.Employee;
import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.evaluation.domain.EmpEvaluatorGlobal;
import com.peoplecore.evaluation.domain.EvalSeasonStatus;
import com.peoplecore.evaluation.domain.Season;
import com.peoplecore.evaluation.dto.EmpEvaluatorChangeRequest;
import com.peoplecore.evaluation.dto.EmpEvaluatorGlobalResponse;
import com.peoplecore.evaluation.dto.EmpEvaluatorMappingDto;
import com.peoplecore.evaluation.dto.EmpEvaluatorUpdateRequest;
import com.peoplecore.evaluation.repository.EmpEvaluatorGlobalRepository;
import com.peoplecore.evaluation.repository.EvalGradeRepository;
import com.peoplecore.evaluation.repository.SeasonRepository;
import com.peoplecore.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// 글로벌 사원-평가자 매핑 관리. 진행 중 시즌이 있으면 신규 evaluatee 추가 차단, 평가자 변경만 허용.
@Service
@Transactional
@Slf4j
public class EmpEvaluatorService {

    private final EmpEvaluatorGlobalRepository globalRepository;
    private final EmployeeRepository employeeRepository;
    private final SeasonRepository seasonRepository;
    private final EvalGradeRepository evalGradeRepository;
    private final DepartmentRepository departmentRepository;

    public EmpEvaluatorService(EmpEvaluatorGlobalRepository globalRepository,
                               EmployeeRepository employeeRepository,
                               SeasonRepository seasonRepository,
                               EvalGradeRepository evalGradeRepository,
                               DepartmentRepository departmentRepository) {
        this.globalRepository = globalRepository;
        this.employeeRepository = employeeRepository;
        this.seasonRepository = seasonRepository;
        this.evalGradeRepository = evalGradeRepository;
        this.departmentRepository = departmentRepository;
    }

    // 부서 계층 검증 — 평가자 부서가 피평가자 부서와 같거나 상위(ancestor)인지 판정
    //   타부서(형제/사촌)는 평가자로 지정 불가
    //   parentMap: deptId -> parentDeptId (null=루트)
    private boolean isEvaluatorDeptValid(Long evaluatorDeptId, Long evaluateeDeptId,
                                          Map<Long, Long> parentMap) {
        if (evaluatorDeptId == null || evaluateeDeptId == null) return false;
        // 같은 부서
        if (evaluatorDeptId.equals(evaluateeDeptId)) return true;
        // 상위 부서들 — evaluatee 의 parent 체인을 타고 올라가며 매칭
        Long cursor = parentMap.get(evaluateeDeptId);
        Set<Long> visited = new HashSet<>();   // 순환 방어 (데이터 이상 시 무한루프 방지)
        while (cursor != null && visited.add(cursor)) {
            if (cursor.equals(evaluatorDeptId)) return true;
            cursor = parentMap.get(cursor);
        }
        return false;
    }

    // 회사의 부서 parent 맵 구축 (활성 부서만)
    private Map<Long, Long> buildDeptParentMap(UUID companyId) {
        List<Department> all = departmentRepository.findByCompany_CompanyIdAndIsUseOrderByDeptNameAsc(
            companyId, UseStatus.Y);
        Map<Long, Long> map = new HashMap<>();
        for (Department d : all) {
            map.put(d.getDeptId(), d.getParentDeptId());
        }
        return map;
    }

    // 회사 전체 글로벌 매핑 조회. 진행 중 시즌이 있으면 그 시즌의 평가 대상자만 노출 -> 신규 입사자 자동 제외.
    // 매핑 페이지 진입 시 호출. 시즌 상태에 따라 데이터 출처가 다름:
    //   - 진행 중 시즌 있음 → EvalGrade 박제값 기반 (시즌 OPEN 시점 박제, 평가자 퇴사 시 evaluator null)
    //   - 진행 중 시즌 없음 → 글로벌 매핑 기반 (HR이 다음 시즌 준비용으로 자유 편집)
    @Transactional(readOnly = true)
    public EmpEvaluatorGlobalResponse getGlobal(UUID companyId) {

        Optional<Season> openSeasonOpt = seasonRepository
            .findByCompany_CompanyIdAndStatus(companyId, EvalSeasonStatus.OPEN);

        List<EmpEvaluatorMappingDto> dtos = new ArrayList<>();

        if (openSeasonOpt.isPresent()) {
            // 진행 중 시즌 — EvalGrade 박제 기반
            Long openSeasonId = openSeasonOpt.get().getSeasonId();
            List<com.peoplecore.evaluation.domain.EvalGrade> grades =
                evalGradeRepository.findBySeason_SeasonId(openSeasonId);
            for (com.peoplecore.evaluation.domain.EvalGrade g : grades) {
                dtos.add(fromEvalGrade(g));
            }
        } else {
            // 시즌 없음/DRAFT/CLOSED — 글로벌 매핑 기반
            List<EmpEvaluatorGlobal> rows = globalRepository.findByCompanyId(companyId);
            for (EmpEvaluatorGlobal r : rows) {
                dtos.add(toDto(r));
            }
        }

        return EmpEvaluatorGlobalResponse.builder()
            .mappings(dtos)
            .build();
    }

    // EvalGrade → MappingDto 변환 (진행 중 시즌 표시용)
    private EmpEvaluatorMappingDto fromEvalGrade(com.peoplecore.evaluation.domain.EvalGrade g) {
        Employee tee = g.getEmp();
        return EmpEvaluatorMappingDto.builder()
            .evaluateeEmpId(tee.getEmpId())
            .evaluateeName(tee.getEmpName())
            .evaluateeDeptName(g.getDeptNameSnapshot() != null
                ? g.getDeptNameSnapshot()
                : (tee.getDept() != null ? tee.getDept().getDeptName() : null))
            .evaluatorEmpId(g.getEvaluatorIdSnapshot())   // null 가능 (퇴사로 풀린 행)
            .evaluatorName(g.getEvaluatorNameSnapshot())
            .evaluatorDeptName(null)  // 박제 시 부서명 따로 안 박음 — 필요하면 join 추가
            .excluded(false)  // EvalGrade row 자체가 있으면 제외 아님 (제외자는 row 안 만들어짐)
            .build();
    }

    // 글로벌 매핑 일괄 교체. 진행 중 시즌이 있으면 차단 (시즌 중엔 EvalGrade 박제 기준으로 평가자 결정됨).
    public EmpEvaluatorGlobalResponse updateGlobal(UUID companyId, EmpEvaluatorUpdateRequest request) {

        // 진행 중 시즌 있으면 — 글로벌 매핑 일괄 변경 차단 (시즌 진행 중엔 박제 잠금)
        if (seasonRepository.findByCompany_CompanyIdAndStatus(companyId, EvalSeasonStatus.OPEN).isPresent()) {
            throw new BusinessException(
                "진행 중 시즌이 있어 글로벌 매핑을 변경할 수 없습니다. 시즌 종료 후 작업하세요.",
                HttpStatus.CONFLICT);
        }

        // 검증 — XOR (excluded=true 면 evaluator null / excluded=false 면 evaluator 필수) + self assignment
        for (EmpEvaluatorUpdateRequest.MappingItem item : request.getMappings()) {
            if (item.isExcluded()) {
                if (item.getEvaluatorEmpId() != null) {
                    throw new BusinessException(
                        "평가 제외 사원은 평가자를 지정할 수 없습니다 (empId=" + item.getEvaluateeEmpId() + ")",
                        HttpStatus.BAD_REQUEST);
                }
            } else {
                if (item.getEvaluatorEmpId() == null) {
                    throw new BusinessException(
                        "평가 제외가 아니면 평가자를 지정해야 합니다 (empId=" + item.getEvaluateeEmpId() + ")",
                        HttpStatus.BAD_REQUEST);
                }
                if (item.getEvaluateeEmpId().equals(item.getEvaluatorEmpId())) {
                    throw new BusinessException(
                        "본인을 본인의 평가자로 지정할 수 없습니다 (empId=" + item.getEvaluateeEmpId() + ")",
                        HttpStatus.BAD_REQUEST);
                }
            }
        }

        // 사원 일괄 로드 + 회사 검증 (evaluator null 인 경우는 제외)
        Set<Long> empIds = new HashSet<>();
        for (EmpEvaluatorUpdateRequest.MappingItem item : request.getMappings()) {
            empIds.add(item.getEvaluateeEmpId());
            if (item.getEvaluatorEmpId() != null) {
                empIds.add(item.getEvaluatorEmpId());
            }
        }
        Map<Long, Employee> empMap = new HashMap<>();
        for (Employee e : employeeRepository.findAllById(empIds)) {
            if (e.getCompany() == null || !e.getCompany().getCompanyId().equals(companyId)) {
                throw new BusinessException(
                    "다른 회사의 사원은 매핑할 수 없습니다 (empId=" + e.getEmpId() + ")",
                    HttpStatus.BAD_REQUEST);
            }
            empMap.put(e.getEmpId(), e);
        }

        // 부서 계층 검증용 parent 맵 — 평가자가 피평가자의 같은/상위 부서인지 체크
        Map<Long, Long> deptParentMap = buildDeptParentMap(companyId);

        // 기존 매핑 삭제 -> 새 매핑 일괄 insert
        globalRepository.deleteByCompanyId(companyId);
        globalRepository.flush();

        List<EmpEvaluatorGlobal> toSave = new ArrayList<>();
        for (EmpEvaluatorUpdateRequest.MappingItem item : request.getMappings()) {
            Employee tee = empMap.get(item.getEvaluateeEmpId());
            if (tee == null) {
                throw new BusinessException(
                    "피평가자 사원을 찾을 수 없습니다 (empId=" + item.getEvaluateeEmpId() + ")",
                    HttpStatus.BAD_REQUEST);
            }
            Employee tor = null;
            if (!item.isExcluded()) {
                tor = empMap.get(item.getEvaluatorEmpId());
                if (tor == null) {
                    throw new BusinessException(
                        "평가자 사원을 찾을 수 없습니다 (empId=" + item.getEvaluatorEmpId() + ")",
                        HttpStatus.BAD_REQUEST);
                }
                // 부서 계층 검증 — 같은 부서 또는 상위 부서만 평가자로 지정 가능
                Long teeDeptId = tee.getDept() != null ? tee.getDept().getDeptId() : null;
                Long torDeptId = tor.getDept() != null ? tor.getDept().getDeptId() : null;
                if (!isEvaluatorDeptValid(torDeptId, teeDeptId, deptParentMap)) {
                    throw new BusinessException(
                        "평가자는 피평가자와 같은 부서이거나 상위 부서 소속이어야 합니다 ("
                            + tee.getEmpName() + " ← " + tor.getEmpName() + ")",
                        HttpStatus.BAD_REQUEST);
                }
            }
            toSave.add(EmpEvaluatorGlobal.builder()
                .companyId(companyId)
                .evaluatee(tee)
                .evaluator(tor)
                .excluded(item.isExcluded())
                .build());
        }
        globalRepository.saveAll(toSave);

        log.info("EmpEvaluator 글로벌 매핑 갱신 companyId={}, count={}", companyId, toSave.size());
        return getGlobal(companyId);
    }

    // 시즌 진행 중 평가자 재지정 — 평가자 퇴사로 미지정(null) 된 EvalGrade 행에 한해 새 평가자 지정.
    // 일반 매핑된 행(evaluator_id_snapshot 있는 행)은 변경 불가 (박제 잠금).
    public void reassignDuringSeason(UUID companyId, Long evaluateeEmpId, EmpEvaluatorChangeRequest request) {

        Season openSeason = seasonRepository.findByCompany_CompanyIdAndStatus(companyId, EvalSeasonStatus.OPEN)
            .orElseThrow(() -> new BusinessException(
                "진행 중인 시즌이 없습니다.", HttpStatus.BAD_REQUEST));

        // 그 시즌의 EvalGrade 조회
        com.peoplecore.evaluation.domain.EvalGrade row = evalGradeRepository
            .findByEmp_EmpIdAndSeason_SeasonId(evaluateeEmpId, openSeason.getSeasonId())
            .orElseThrow(() -> new BusinessException(
                "해당 시즌의 평가 대상자가 아닙니다.", HttpStatus.NOT_FOUND));

        // 박제 잠금 — 이미 매핑된 행은 변경 불가
        if (row.getEvaluatorIdSnapshot() != null) {
            throw new BusinessException(
                "이미 평가자가 지정된 행은 변경할 수 없습니다. 퇴사로 풀린 미지정 행만 가능.",
                HttpStatus.CONFLICT);
        }

        // self assignment 차단
        if (evaluateeEmpId.equals(request.getNewEvaluatorId())) {
            throw new BusinessException(
                "본인을 본인의 평가자로 지정할 수 없습니다.", HttpStatus.BAD_REQUEST);
        }

        // 새 평가자 검증 — 회사 소속 + 재직중
        Employee newEvaluator = employeeRepository.findById(request.getNewEvaluatorId())
            .orElseThrow(() -> new BusinessException(
                "새 평가자를 찾을 수 없습니다.", HttpStatus.BAD_REQUEST));
        if (newEvaluator.getCompany() == null
            || !newEvaluator.getCompany().getCompanyId().equals(companyId)) {
            throw new BusinessException(
                "다른 회사의 사원은 평가자로 지정할 수 없습니다.", HttpStatus.BAD_REQUEST);
        }
        if (!com.peoplecore.employee.domain.EmpStatus.ACTIVE.equals(newEvaluator.getEmpStatus())) {
            throw new BusinessException(
                "재직중인 사원만 평가자로 지정할 수 있습니다.", HttpStatus.BAD_REQUEST);
        }

        // 부서 계층 검증 — 같은 부서 또는 상위 부서만 평가자로 지정 가능
        Employee tee = row.getEmp();
        Long teeDeptId = tee != null && tee.getDept() != null ? tee.getDept().getDeptId() : null;
        Long torDeptId = newEvaluator.getDept() != null ? newEvaluator.getDept().getDeptId() : null;
        Map<Long, Long> deptParentMap = buildDeptParentMap(companyId);
        if (!isEvaluatorDeptValid(torDeptId, teeDeptId, deptParentMap)) {
            throw new BusinessException(
                "평가자는 피평가자와 같은 부서이거나 상위 부서 소속이어야 합니다.",
                HttpStatus.BAD_REQUEST);
        }

        // EvalGrade evaluator snapshot update
        row.changeEvaluator(newEvaluator.getEmpId(), newEvaluator.getEmpName());

        log.info("EvalGrade evaluator 재지정 seasonId={}, evaluateeEmpId={}, newEvaluatorId={}",
            openSeason.getSeasonId(), evaluateeEmpId, newEvaluator.getEmpId());
    }

    // 평가 제외 토글 — 시즌 OPEN 중이면 차단 (시즌 중엔 EvalGrade 박제 기준).
    public EmpEvaluatorMappingDto markExcluded(UUID companyId, Long evaluateeEmpId) {
        if (seasonRepository.findByCompany_CompanyIdAndStatus(companyId, EvalSeasonStatus.OPEN).isPresent()) {
            throw new BusinessException(
                "진행 중 시즌이 있어 평가 제외를 변경할 수 없습니다.", HttpStatus.CONFLICT);
        }
        EmpEvaluatorGlobal mapping = globalRepository
            .findByCompanyIdAndEvaluatee_EmpId(companyId, evaluateeEmpId)
            .orElse(null);

        if (mapping == null) {
            // row 없으면 신규 생성 (excluded=true)
            Employee tee = employeeRepository.findById(evaluateeEmpId)
                .orElseThrow(() -> new BusinessException(
                    "사원을 찾을 수 없습니다.", HttpStatus.BAD_REQUEST));
            if (tee.getCompany() == null || !tee.getCompany().getCompanyId().equals(companyId)) {
                throw new BusinessException(
                    "다른 회사의 사원입니다.", HttpStatus.BAD_REQUEST);
            }
            mapping = globalRepository.save(EmpEvaluatorGlobal.builder()
                .companyId(companyId)
                .evaluatee(tee)
                .evaluator(null)
                .excluded(true)
                .build());
        } else {
            mapping.markExcluded();
        }

        log.info("EmpEvaluator 평가 제외 처리 companyId={}, evaluateeEmpId={}", companyId, evaluateeEmpId);
        return toDto(mapping);
    }

    // 매핑 -> DTO
    private EmpEvaluatorMappingDto toDto(EmpEvaluatorGlobal r) {
        Employee tee = r.getEvaluatee();
        Employee tor = r.getEvaluator();
        return EmpEvaluatorMappingDto.builder()
            .evaluateeEmpId(tee.getEmpId())
            .evaluateeName(tee.getEmpName())
            .evaluateeDeptName(tee.getDept() != null ? tee.getDept().getDeptName() : null)
            .evaluatorEmpId(tor != null ? tor.getEmpId() : null)
            .evaluatorName(tor != null ? tor.getEmpName() : null)
            .evaluatorDeptName(tor != null && tor.getDept() != null ? tor.getDept().getDeptName() : null)
            .excluded(r.isExcluded())
            .build();
    }
}
