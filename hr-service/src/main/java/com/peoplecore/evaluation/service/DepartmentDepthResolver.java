package com.peoplecore.evaluation.service;

import com.peoplecore.department.domain.Department;
import com.peoplecore.department.domain.UseStatus;
import com.peoplecore.department.repository.DepartmentRepository;
import com.peoplecore.evaluation.domain.KpiOption;
import com.peoplecore.evaluation.domain.KpiOptionType;
import com.peoplecore.evaluation.repository.KpiOptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// KPI 옵션 depth 정책 + 조직도 -> 유효 부서 ID Set 산출
//   - "leaf" : 자식 없는 모든 부서
//   - "N"    : depth==N 노드 + depth<N 인 리프 노드까지 (A안 로직)
@Service
@Transactional(readOnly = true)
public class DepartmentDepthResolver {

    private final KpiOptionRepository kpiOptionRepository;
    private final DepartmentRepository departmentRepository;

    public DepartmentDepthResolver(KpiOptionRepository kpiOptionRepository,
                                   DepartmentRepository departmentRepository) {
        this.kpiOptionRepository = kpiOptionRepository;
        this.departmentRepository = departmentRepository;
    }

    // 회사의 depth 정책에 부합하는 부서 ID Set 반환
    public Set<Long> resolveValidDeptIds(UUID companyId) {

        // 1. depth 정책 가져오기 (없으면 leaf 기본)
        KpiOption deptOpt = kpiOptionRepository.findFirstByCompany_CompanyIdAndType(companyId, KpiOptionType.DEPARTMENT).orElse(null);
        String level = (deptOpt != null) ? deptOpt.getOptionValue() : "leaf";

        // 2. 회사 전체 활성 부서 로드
        List<Department> all = departmentRepository.findByCompany_CompanyIdAndIsUseOrderByDeptNameAsc(companyId, UseStatus.Y);
        if (all.isEmpty()) return Collections.emptySet();

        // 3. parent -> children 맵 + 루트 추출
        Map<Long, List<Department>> childrenMap = new HashMap<>();
        List<Department> roots = new ArrayList<>();
        for (Department d : all) {
            if (d.getParentDeptId() == null) {
                roots.add(d);
            } else {
                Long parentId = d.getParentDeptId();
                List<Department> children = childrenMap.get(parentId);
                if (children == null) {
                    children = new ArrayList<>();
                    childrenMap.put(parentId, children);
                }
                children.add(d);
            }
        }

        // 4. depth 기반 필터
        Set<Long> result = new HashSet<>();

        if ("leaf".equals(level)) {
            // 리프 노드(자식 없는 모든 부서) 수집
            for (Department d : all) {
                List<Department> children = childrenMap.get(d.getDeptId());
                if (children == null || children.isEmpty()) {
                    result.add(d.getDeptId());
                }
            }
            return result;
        }

        // N단계 = depth==N 노드 + depth<N 인 리프 (A안)
        int targetDepth;
        try {
            targetDepth = Integer.parseInt(level);
        } catch (NumberFormatException e) {
            targetDepth = 1;
        }
        for (Department root : roots) {
            walkByDepth(root, 1, targetDepth, childrenMap, result);
        }
        return result;
    }

    // 재귀로 트리 walk 하며 N단계 부서 수집
    private void walkByDepth(Department node, int depth, int targetDepth, Map<Long, List<Department>> childrenMap, Set<Long> result) {
        List<Department> children = childrenMap.get(node.getDeptId());
        boolean isLeaf = (children == null || children.isEmpty());

        if (depth == targetDepth) {
            result.add(node.getDeptId());
        } else if (depth < targetDepth && isLeaf) {
            result.add(node.getDeptId());
        } else if (!isLeaf && depth < targetDepth) {
            for (Department child : children) {
                walkByDepth(child, depth + 1, targetDepth, childrenMap, result);
            }
        }
    }
}
