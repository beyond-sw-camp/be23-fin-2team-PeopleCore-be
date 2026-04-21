package com.peoplecore.evaluation.service;

import com.peoplecore.company.domain.Company;
import com.peoplecore.company.repository.CompanyRepository;
import com.peoplecore.evaluation.domain.KpiOption;
import com.peoplecore.evaluation.domain.KpiOptionType;
import com.peoplecore.evaluation.dto.KpiOptionBundleRequest;
import com.peoplecore.evaluation.dto.KpiOptionBundleRequest.ItemRequest;
import com.peoplecore.evaluation.dto.KpiOptionBundleResponse;
import com.peoplecore.evaluation.repository.KpiOptionRepository;
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

// KPI 옵션 서비스 - 회사별 카테고리/단위/부서depth 일괄 관리 (diff-based + id 매칭)
@Service
@Transactional(readOnly = true)
public class KpiOptionService {

    private final KpiOptionRepository repository;
    private final CompanyRepository companyRepository;

    public KpiOptionService(KpiOptionRepository repository, CompanyRepository companyRepository) {
        this.repository = repository;
        this.companyRepository = companyRepository;
    }

    // 기본값 ────────────────────────────
    private static final List<String> DEFAULT_CATEGORIES =
            List.of("업무성과", "역량개발", "조직기여");

    private static final List<String> DEFAULT_UNITS =
            List.of("%", "건", "원", "시간", "점", "일");

    private static final String DEFAULT_DEPARTMENT_LEVEL = "leaf";

    // 1. 조회 — 최초 진입 시 기본값 seed 후 반환
    @Transactional
    public KpiOptionBundleResponse getOptions(UUID companyId) {
        // 활성 row 한 번에 로드 (type + sortOrder 정렬)
        List<KpiOption> rows = repository.findByCompany_CompanyIdAndIsActiveTrueOrderByTypeAscSortOrderAsc(companyId);

        // 회사 최초면 기본값 insert
        if (rows.isEmpty()) {
            rows = seedDefaults(companyId);
        }

        return KpiOptionBundleResponse.from(rows);
    }

    // 2. 저장 — diff 기반 (rename/추가/삭제/재정렬 각각 최소 쿼리만 날림)
    @Transactional
    public KpiOptionBundleResponse saveOptions(UUID companyId, KpiOptionBundleRequest req) {
        Company company = getCompanyOrThrow(companyId);

        // 타입별 개별 동기화
        syncListType(companyId, company, KpiOptionType.CATEGORY, req.getCategories());
        syncListType(companyId, company, KpiOptionType.UNIT, req.getUnits());
        syncDepartmentLevel(companyId, company, req.getDepartmentLevel());

        // 반영 직후 최신 상태로 응답 재조립
        List<KpiOption> latest = repository.findByCompany_CompanyIdAndIsActiveTrueOrderByTypeAscSortOrderAsc(companyId);
        return KpiOptionBundleResponse.from(latest);
    }

    // 3. 리셋 — 기본값으로 diff 동기화 (기존 row 를 인덱스로 재활용 -> rename 만으로 복원)
    @Transactional
    public KpiOptionBundleResponse resetOptions(UUID companyId) {
        Company company = getCompanyOrThrow(companyId);

        // 기존 row 를 type 별로 꺼내 기본값과 인덱스 매칭 -> FK 유지 + 비활성 row 누적 방지
        List<KpiOption> existingCategories = repository.findByCompany_CompanyIdAndTypeAndIsActiveTrueOrderBySortOrderAsc(companyId, KpiOptionType.CATEGORY);
        List<KpiOption> existingUnits = repository.findByCompany_CompanyIdAndTypeAndIsActiveTrueOrderBySortOrderAsc(companyId, KpiOptionType.UNIT);

        syncListType(companyId, company, KpiOptionType.CATEGORY,
                buildResetRequests(existingCategories, DEFAULT_CATEGORIES));
        syncListType(companyId, company, KpiOptionType.UNIT,
                buildResetRequests(existingUnits, DEFAULT_UNITS));
        syncDepartmentLevel(companyId, company, DEFAULT_DEPARTMENT_LEVEL);

        List<KpiOption> latest = repository.findByCompany_CompanyIdAndIsActiveTrueOrderByTypeAscSortOrderAsc(companyId);
        return KpiOptionBundleResponse.from(latest);
    }

    // ── private helpers ──────────────────────

    // 회사 엔티티 로드 (FK 용)
    private Company getCompanyOrThrow(UUID companyId) {
        Company company = companyRepository.findById(companyId).orElse(null);
        if (company == null) {
            throw new IllegalArgumentException("회사를 찾을 수 없습니다: " + companyId);
        }
        return company;
    }

    // 리스트형(CATEGORY/UNIT) 옵션 diff 동기화
    //   - id 있음 -> 기존 row 매칭. label 바뀌었으면 rename, 순서 바뀌었으면 sortOrder UPDATE
    //   - id null -> 신규 INSERT
    //   - 기존에 있는데 요청에 id 없음 → soft delete
    private void syncListType(UUID companyId, Company company, KpiOptionType type, List<ItemRequest> requested) {

        // 현재 DB 활성 row 로드
        List<KpiOption> existing = repository.findByCompany_CompanyIdAndTypeAndIsActiveTrueOrderBySortOrderAsc(companyId, type);

        // id -> 엔티티 맵 (빠른 매칭)
        Map<Long, KpiOption> existingById = new HashMap<>();
        for (KpiOption o : existing) {
            existingById.put(o.getOptionId(), o);
        }

        // 요청에 담긴 id 집합 — 여기 없는 기존 row 는 삭제 대상
        Set<Long> keepIds = new HashSet<>();
        for (ItemRequest item : requested) {
            if (item.getId() != null) {
                keepIds.add(item.getId());
            }
        }

        // 1 삭제: 기존에 있는데 요청에 없는 것 -> soft delete
        List<KpiOption> toDeactivate = new ArrayList<>();
        for (KpiOption o : existing) {
            if (!keepIds.contains(o.getOptionId())) {
                o.deactivate();
                toDeactivate.add(o);
            }
        }
        if (!toDeactivate.isEmpty()) {
            repository.saveAll(toDeactivate);
        }

        // (2) 신규/rename/재정렬: 요청 순서대로 처리
        int order = 0;
        List<KpiOption> toSave = new ArrayList<>();

        for (ItemRequest item : requested) {

            // id null -> 신규 추가
            if (item.getId() == null) {
                toSave.add(KpiOption.builder()
                        .company(company).type(type)
                        .optionValue(item.getLabel())
                        .sortOrder(order).isActive(true).build());
                order++;
                continue;
            }

            // id 있음 -> 기존 row 매칭
            KpiOption entity = existingById.get(item.getId());
            if (entity == null) {
                // 잘못된 id — 방어적으로 신규 INSERT 처리
                toSave.add(KpiOption.builder()
                        .company(company).type(type)
                        .optionValue(item.getLabel())
                        .sortOrder(order).isActive(true).build());
                order++;
                continue;
            }

            // label 이 달라졌으면 rename
            boolean changed = false;
            if (!entity.getOptionValue().equals(item.getLabel())) {
                entity.updateValue(item.getLabel());
                changed = true;
            }

            // 순서가 달라졌으면 sortOrder UPDATE
            if (entity.getSortOrder() == null || entity.getSortOrder() != order) {
                entity.updateSortOrder(order);
                changed = true;
            }

            // 아무 것도 안 변했으면 쿼리 안 나감
            if (changed) {
                toSave.add(entity);
            }
            order++;
        }

        if (!toSave.isEmpty()) {
            repository.saveAll(toSave);
        }
    }

    // DEPARTMENT 단일 행 동기화 — optionValue(depth) 다르면 UPDATE, row 없으면 INSERT
    private void syncDepartmentLevel(UUID companyId, Company company, String newLevel) {
        Optional<KpiOption> existing = repository
                .findFirstByCompany_CompanyIdAndType(companyId, KpiOptionType.DEPARTMENT);

        if (existing.isPresent()) {
            KpiOption dept = existing.get();
            if (!newLevel.equals(dept.getOptionValue())) {
                dept.updateValue(newLevel);
                repository.save(dept);
            }
            return;
        }

        // row 없으면 최초 INSERT
        repository.save(KpiOption.builder()
                .company(company).type(KpiOptionType.DEPARTMENT)
                .optionValue(newLevel)
                .sortOrder(0).isActive(true).build());
    }

    // 리셋용 - 기존 row 를 인덱스로 재활용해서 rename 만으로 기본값 복원(변경사항만 되돌림)
    //   - 기존 row 개수 > 기본값 개수: 초과분은 요청에 포함 안 됨 -> syncListType 에서 soft delete
    //   - 기존 row 개수 < 기본값 개수: 부족분은 id=null 로 신규 INSERT
    //   - 같은 위치의 label 이 같으면 diff 에서 쿼리 0건 (idempotent)
    private List<ItemRequest> buildResetRequests(List<KpiOption> existingInOrder, List<String> defaults) {
        List<ItemRequest> result = new ArrayList<>();
        for (int i = 0; i < defaults.size(); i++) {
            Long reuseId = i < existingInOrder.size()
                    ? existingInOrder.get(i).getOptionId()
                    : null;
            result.add(ItemRequest.builder()
                    .id(reuseId)
                    .label(defaults.get(i))
                    .build());
        }
        return result;
    }

    // 회사 최초 조회 시 기본값 seed
    private List<KpiOption> seedDefaults(UUID companyId) {
        Company company = getCompanyOrThrow(companyId);
        List<KpiOption> defaults = new ArrayList<>();

        // 카테고리 기본 3개
        int order = 0;
        for (String c : DEFAULT_CATEGORIES) {
            defaults.add(KpiOption.builder()
                    .company(company).type(KpiOptionType.CATEGORY)
                    .optionValue(c).sortOrder(order).isActive(true).build());
            order++;
        }

        // 단위 기본 6개
        order = 0;
        for (String u : DEFAULT_UNITS) {
            defaults.add(KpiOption.builder()
                    .company(company).type(KpiOptionType.UNIT)
                    .optionValue(u).sortOrder(order).isActive(true).build());
            order++;
        }

        // 부서 depth 기본 1행
        defaults.add(KpiOption.builder()
                .company(company).type(KpiOptionType.DEPARTMENT)
                .optionValue(DEFAULT_DEPARTMENT_LEVEL)
                .sortOrder(0).isActive(true).build());

        return repository.saveAll(defaults);
    }
}
