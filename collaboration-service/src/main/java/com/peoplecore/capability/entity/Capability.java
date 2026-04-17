package com.peoplecore.capability.entity;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * 파일함 등 협업 도메인에서 사용되는 권한(Capability) 정의.
 *
 * <p>시스템 정의 고정 코드 목록이며, 회사/직책 별 커스터마이징은
 * {@link TitleCapability} 매핑 테이블로 표현된다.</p>
 *
 * <p>코드 prefix 규약:</p>
 * <ul>
 *   <li>{@code FILE_*} : 파일함 관련 권한</li>
 * </ul>
 */
@Entity
@Table(name = "collab_capability")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Capability extends BaseTimeEntity {

    /**
     * 권한 코드 (예: {@code FILE_CREATE_DEPT_FOLDER}). 시스템 정의 상수.
     */
    @Id
    @Column(name = "capability_code", length = 64)
    private String code;

    /**
     * 사람이 읽을 수 있는 설명 (관리 UI에서 표시).
     */
    @Column(nullable = false, length = 255)
    private String description;

    /**
     * 카테고리 (예: {@code FILE}, 추후 {@code BOARD} 등 확장).
     */
    @Column(nullable = false, length = 32)
    private String category;

    /**
     * 적용 범위 (참고용 메타데이터). 예: {@code DEPT}, {@code COMPANY}, {@code PERSONAL}.
     */
    @Column(nullable = false, length = 32)
    private String scope;
}
