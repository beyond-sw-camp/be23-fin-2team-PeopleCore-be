package com.peoplecore.menusetting.service;

import com.peoplecore.employee.domain.EmpRole;
import com.peoplecore.menusetting.domain.SidebarMenu;
import com.peoplecore.menusetting.domain.UserMenuSetting;
import com.peoplecore.menusetting.dto.UserMenuItemRequest;
import com.peoplecore.menusetting.dto.UserMenuItemResponse;
import com.peoplecore.menusetting.dto.UserMenuSettingUpdateRequest;
import com.peoplecore.menusetting.repository.UserMenuSettingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional
public class UserMenuSettingService {

    @Autowired
    private UserMenuSettingRepository repository;

    /**
     * 내 사이드바 설정 조회
     * 1) 설정 없으면 enum 전체를 기본값으로 lazy-insert
     * 2) requiredRoles 에 포함되지 않는 역할은 해당 메뉴를 응답에서 제외
     * 3) alwaysOn 메뉴는 isVisible=true 강제, 나머지는 DB 값 사용
     * 4) sort_order 오름차순 정렬
     */
    public List<UserMenuItemResponse> getMySettings(Long empId, EmpRole role) {
        if (!repository.existsByEmpId(empId)) {
            initDefault(empId);
        }
        List<UserMenuSetting> list = repository.findByEmpIdOrderBySortOrderAsc(empId);
        list = ensureAllMenusPresent(empId, list);

        List<UserMenuItemResponse> result = new ArrayList<>(list.size());
        for (UserMenuSetting e : list) {
            SidebarMenu menu = e.getMenuCode();
            // 역할 부족으로 접근 불가한 메뉴는 응답에서 제거
            if (!menu.isAccessibleBy(role)) continue;
            // alwaysOn 은 항상 true, 나머지는 DB 값 반영
            boolean effectiveVisible = menu.isAlwaysOn() || Boolean.TRUE.equals(e.getIsVisible());
            result.add(UserMenuItemResponse.of(e, effectiveVisible));
        }
        return result;
    }

    /**
     * 내 사이드바 설정 일괄 저장 (토글 + 순서)
     * - alwaysOn 메뉴는 isVisible 요청값 무시하고 true 강제
     * - 그 외 메뉴는 요청값 그대로 반영
     * - sortOrder 는 모든 메뉴에 반영
     * - 요청에 누락된 메뉴는 기존 값 유지
     * - 알 수 없는 menuCode 전달 시 IllegalArgumentException
     */
    public List<UserMenuItemResponse> updateMySettings(Long empId, EmpRole role,
                                                      UserMenuSettingUpdateRequest request) {
        if (!repository.existsByEmpId(empId)) {
            initDefault(empId);
        }

        // 기존 설정을 menu_code 로 인덱싱 (신규 enum 누락분은 lazy-insert)
        List<UserMenuSetting> current = ensureAllMenusPresent(empId,
                repository.findByEmpIdOrderBySortOrderAsc(empId));
        Map<SidebarMenu, UserMenuSetting> existing = new EnumMap<>(SidebarMenu.class);
        for (UserMenuSetting e : current) {
            existing.put(e.getMenuCode(), e);
        }

        for (UserMenuItemRequest item : request.getItems()) {
            SidebarMenu menu;
            try {
                menu = SidebarMenu.valueOf(item.getMenuCode());
            } catch (IllegalArgumentException ex) {
                // 프론트에서 잘못된 menuCode 가 올 경우
                throw new IllegalArgumentException("알 수 없는 메뉴 코드입니다: " + item.getMenuCode());
            }

            UserMenuSetting entity = existing.get(menu);
            if (entity == null) continue; // initDefault 이후에는 발생하지 않음

            // 순서는 모든 메뉴에 반영
            entity.changeSortOrder(item.getSortOrder());

            // alwaysOn 은 true 강제, 나머지는 요청값 반영
            if (menu.isAlwaysOn()) {
                entity.changeVisibility(true);
            } else {
                entity.changeVisibility(Boolean.TRUE.equals(item.getIsVisible()));
            }
        }

        return getMySettings(empId, role);
    }

    /**
     * enum에 새 메뉴가 추가되었는데 기존 사용자에게 row가 없는 경우 lazy-insert.
     * - initDefault 이후 enum 항목이 늘어났을 때 자동 동기화 용도
     * - 누락 메뉴는 enum 선언 순서대로 처리:
     *   1) 해당 메뉴의 defaultOrder 를 삽입 지점으로 사용
     *   2) 기존 row 중 sortOrder >= 삽입 지점 인 것들을 +1 시프트 (충돌 방지)
     *   3) 누락 메뉴를 isVisible=true / sortOrder=defaultOrder 로 INSERT
     *   클래스 레벨 @Transactional 로 dirty checking 시프트가 같은 트랜잭션에서 flush 됨
     */
    private List<UserMenuSetting> ensureAllMenusPresent(Long empId, List<UserMenuSetting> existing) {
        if (existing.size() == SidebarMenu.values().length) return existing;

        Set<SidebarMenu> have = EnumSet.noneOf(SidebarMenu.class);
        Map<SidebarMenu, UserMenuSetting> byCode = new EnumMap<>(SidebarMenu.class);
        for (UserMenuSetting e : existing) {
            have.add(e.getMenuCode());
            byCode.put(e.getMenuCode(), e);
        }

        for (SidebarMenu menu : SidebarMenu.values()) {
            if (have.contains(menu)) continue;

            int insertAt = menu.getDefaultOrder();
            // 삽입 지점 이후 기존 메뉴 sort_order +1 시프트
            for (UserMenuSetting e : byCode.values()) {
                if (e.getSortOrder() != null && e.getSortOrder() >= insertAt) {
                    e.changeSortOrder(e.getSortOrder() + 1);
                }
            }

            UserMenuSetting created = UserMenuSetting.builder()
                    .empId(empId)
                    .menuCode(menu)
                    .isVisible(true)
                    .sortOrder(insertAt)
                    .build();
            repository.save(created);
            byCode.put(menu, created);
            have.add(menu);
        }

        return repository.findByEmpIdOrderBySortOrderAsc(empId);
    }

    /**
     * 사원 최초 조회 시 enum 전체를 defaultOrder / 기본 visible=true 로 일괄 insert
     * - 중복 insert 방지용으로 호출부에서 existsByEmpId 체크 필수
     */
    private void initDefault(Long empId) {
        List<UserMenuSetting> defaults = new ArrayList<>(SidebarMenu.values().length);
        for (SidebarMenu menu : SidebarMenu.values()) {
            defaults.add(UserMenuSetting.builder()
                    .empId(empId)
                    .menuCode(menu)
                    .isVisible(true)
                    .sortOrder(menu.getDefaultOrder())
                    .build());
        }
        repository.saveAll(defaults);
    }
}
