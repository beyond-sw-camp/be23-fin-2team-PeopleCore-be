package com.peoplecore.vacation.entity;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;


import java.math.BigDecimal;

/**
 * 휴가유형
 */
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacationInfo extends BaseTimeEntity {

    /** 휴가 유형 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long infoId;

    /** 회사 ID */
    @Column(nullable = false)
    private UUID companyId;

    /** 휴가 유형명 */
    @Column(nullable = false)
    private String vacTypeName;

    /** 휴가 유형 코드 */
    @Column(nullable = false, length = 50)
    private String vacTypeCode;

    /** 유급여부 */
    @Column(nullable = false)
    private Boolean isPaid;

    /** 차감 일수 */
    @Column(nullable = false)
    private BigDecimal deductDays;

    /** 증빙서류필요여부 */
    @Column(nullable = false)
    private Boolean requiresDoc;

    /** 활성화 여부 */
    @Column(nullable = false)
    private Boolean infoIsActive;

    /** 법정 휴가 유형 */
    @Column(nullable = false)
    private Boolean infoIsLegal;

    /** 정렬 순서 */
    @Column(nullable = false)
    private Integer infoSortOrder;

}
