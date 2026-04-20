package com.peoplecore.filevault.entity;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * 파일함 즐겨찾기.
 *
 * <p>사용자별 폴더/파일 즐겨찾기 매핑. {@code (emp_id, target_type, target_id)} 가
 * 유니크하며, 동일 키 재요청 시 토글로 동작한다(있으면 삭제, 없으면 생성).</p>
 *
 * <p>대상이 휴지통으로 이동하거나 영구 삭제되어도 즐겨찾기 행은 자동으로 정리하지 않는다.
 * 목록 응답 단계에서 active 한 대상만 노출하므로 데이터 일관성에 문제 없음.</p>
 */
@Entity
@Table(
    name = "file_vault_favorite",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_favorite_emp_target",
            columnNames = {"emp_id", "target_type", "target_id"}
        )
    },
    indexes = {
        @Index(name = "idx_favorite_emp_type", columnList = "emp_id,target_type")
    }
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileVaultFavorite extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "favorite_id")
    private Long id;

    @Column(name = "emp_id", nullable = false)
    private Long empId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 16)
    private FavoriteTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;
}
