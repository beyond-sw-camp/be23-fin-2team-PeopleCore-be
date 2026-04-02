package com.peoplecore.collaboration_service.common.entity;

import com.peoplecore.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * 공통 코드
 */
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommonCode extends BaseTimeEntity {

    /** 공통 코드 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long codeId;

    /** 그룹 번호 ID */
    @ManyToOne(fetch = FetchType.LAZY)
    private CommonCodeGroup commonCodeGroup;

    /** 코드값 - group_id+ codevalue */
    @Column(nullable = false)
    private String codeValue;

    /** 코드 이름 - 화면 표시용 */
    @Column(nullable = false)
    private String codeName;

    /** 정렬 순서 - 셀렉 박스 표시 순서 */
    @Column(nullable = false)
    private Integer codeSortOrder;

    /** 활성화 여부 - default == true */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
