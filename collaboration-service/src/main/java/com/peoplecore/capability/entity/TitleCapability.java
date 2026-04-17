package com.peoplecore.capability.entity;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * 직책(Title) ↔ 권한(Capability) 매핑.
 *
 * <p>같은 회사의 같은 직책이라도 SaaS 특성상 회사 관리자가 자유롭게
 * 권한을 부여/회수할 수 있다. 권한 판정은 {@code titleId} 기반이며,
 * {@code companyId} 는 조회/정합성 보조용으로 함께 저장한다.</p>
 */
@Entity
@Table(
    name = "collab_title_capability",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_collab_title_capability",
        columnNames = {"title_id", "capability_code"}
    ),
    indexes = {
        @Index(name = "idx_title_capability_title", columnList = "title_id"),
        @Index(name = "idx_title_capability_company", columnList = "company_id")
    }
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TitleCapability extends BaseTimeEntity {

    /**
     * 매핑 PK (서로게이트).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * hr-service 의 직책(title) 식별자. 서비스 경계를 넘으므로 FK 없이 참조만 유지.
     */
    @Column(name = "title_id", nullable = false)
    private Long titleId;

    /**
     * hr-service 의 회사 식별자 (회사별 조회/정합성 보조).
     */
    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    /**
     * 권한 코드 ({@link Capability#getCode()}).
     */
    @Column(name = "capability_code", nullable = false, length = 64)
    private String capabilityCode;
}
