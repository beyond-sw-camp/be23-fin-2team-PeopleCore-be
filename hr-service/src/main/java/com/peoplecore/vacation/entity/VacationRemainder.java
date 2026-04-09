package com.peoplecore.vacation.entity;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.*;

/**
 * 휴가 잔여
 */
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacationRemainder extends BaseTimeEntity {

    /** 휴가 잔여 ID */
    @Id
    private Long vacRemId;

    /** 회사 ID */
    @Column(nullable = false)
    private UUID companyId;

    /** 휴가 유형 ID */
    @Column(nullable = false)
    private Long infoId;

    /** 사원 아이디 */
    @Column(nullable = false)
    private Long empId;

    /** 연도 */
    @Column(nullable = false)
    private Integer vacRemYear;

    /** 총 일수 */
    @Column(nullable = false)
    private BigDecimal vacRemTotalDay;

    /** 사용일수 */
    @Column(nullable = false)
    private BigDecimal vacRemUsedDay;

    /** 대기 일수 - 신청했지만 아직 승인 안된 일수 */
    @Column(nullable = false)
    private BigDecimal vacRemPendingDays;

}
