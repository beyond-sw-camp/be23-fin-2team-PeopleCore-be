package com.peoplecore.vacation.entity;

import com.peoplecore.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.*;

/**
 * 휴가 잔여 이력 (히스토리 테이블)
 */
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacationRemainderHistory extends BaseTimeEntity {

    /** 휴가 잔여 이력 Id */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long historyId;

    /** 회사 ID */
    @Column(nullable = false)
    private UUID companyId;

    /** 사원 아이디 */
    @Column(nullable = false)
    private Long empId;

    /** 휴가 잔여 ID */
    @Column(nullable = false)
    private Long vacRemId;

    /** 변동 유형 */
    @Column(nullable = false)
    private String historyChangeType;

    /** 변동 일수 */
    @Column(nullable = false)
    private BigDecimal historyChangeDay;

    /** 변동 전 총 일수 */
    @Column(nullable = false)
    private BigDecimal historyBeforeTotalDay;

    /** 변동 후 일수 */
    @Column(nullable = false)
    private BigDecimal historyAfterTotalDay;

    /** 변동 사유 */
    private String historyReason;

    /** 참조 대상 구분 */
    private String historyRefEntityType;

    /** 참조 대상 번호 */
    private Long historyRefEntityId;

    /** 처리자 id */
    @Column(nullable = false)
    private Long historyManagerId;

}
