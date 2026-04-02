package com.peoplecore.collaboration_service.common.entity;

import com.peoplecore.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

/**
 * 공통 코드 그룹
 */
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"company_id","group_code"}))
public class CommonCodeGroup extends BaseTimeEntity {

    /** 그룹 번호 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long groupId;

    /** 회사 번호 ID */
    @Column(nullable = false)
    private UUID companyId;

    /** 그룹 코드 - company_id + group_code */
    @Column(nullable = false)
    private String groupCode;

    /** 그룹명 - ex) 어떤 부분의 상태명인가 */
    @Column(nullable = false)
    private String groupName;

    /** 설명 */
    private String groupDescription;

    /** 활성화 여부 - true ==활성화 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

}
