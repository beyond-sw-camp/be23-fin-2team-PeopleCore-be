package com.peoplecore.menusetting.repository;

import com.peoplecore.menusetting.domain.UserMenuSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserMenuSettingRepository extends JpaRepository<UserMenuSetting, Long> {

    /** 특정 사원의 모든 메뉴 설정을 sort_order 오름차순으로 조회 */
    List<UserMenuSetting> findByEmpIdOrderBySortOrderAsc(Long empId);

    /** 특정 사원의 설정 존재 여부 (lazy-insert 분기용) */
    boolean existsByEmpId(Long empId);
}
