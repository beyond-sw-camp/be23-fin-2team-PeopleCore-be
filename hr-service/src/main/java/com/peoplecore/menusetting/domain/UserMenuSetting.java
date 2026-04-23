package com.peoplecore.menusetting.domain;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * 사원별 사이드바 메뉴 설정
 * - emp_id + menu_code 는 유일 (UNIQUE)
 * - 토글(on/off) + 순서 변경 모두 저장
 * - ALWAYS/ROLE_BASED 메뉴는 is_visible 이 저장돼 있어도 서비스에서 강제로 true 처리
 */
@Entity
@Table(
        name = "user_menu_setting",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_menu_setting_emp_menu",
                columnNames = {"emp_id", "menu_code"}
        ),
        indexes = @Index(
                name = "idx_user_menu_setting_emp_sort",
                columnList = "emp_id, sort_order"
        )
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserMenuSetting extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "setting_id")
    private Long settingId;

    /* 사원 ID (employee.emp_id 참조, 직접 @ManyToOne 매핑 대신 ID 로 보관 - 조회 최적화) */
    @Column(name = "emp_id", nullable = false)
    private Long empId;

    /* 메뉴 코드 - SidebarMenu enum name 저장 */
    @Enumerated(EnumType.STRING)
    @Column(name = "menu_code", nullable = false, length = 30)
    private SidebarMenu menuCode;

    /* 표시 여부 - TOGGLEABLE 타입에서만 실제 의미 있음 */
    @Column(name = "is_visible", nullable = false)
    private Boolean isVisible;

    /* 사이드바 표시 순서 (오름차순, 1부터) */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    /** 토글 값 변경 (TOGGLEABLE 메뉴용, 서비스에서 타입 검증 후 호출) */
    public void changeVisibility(boolean isVisible) {
        this.isVisible = isVisible;
    }

    /** 순서 변경 */
    public void changeSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
