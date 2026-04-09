package com.peoplecore.vacation.entity;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;

/**
 * 휴가 신청
 */
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VacationReq extends BaseTimeEntity {

    /** 휴가 신청 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long vacReqId;

    /** 회사 ID */
    @Column(nullable = false)
    private UUID companyId;

    /** 휴가 유형 ID */
    @Column(nullable = false)
    private Long infoId;

    /** 사원 아이디 */
    @Column(nullable = false)
    private Long empId;

    /** 사원 이름 */
    @Column(nullable = false)
    private String reqEmpName;

    /** 사원 부서명 */
    @Column(nullable = false)
    private String reqEmpDeptName;

    /** 휴가 시작 */
    @Column(nullable = false)
    private LocalDateTime vacReqStartat;

    /** 휴가 종료일 */
    @Column(nullable = false)
    private LocalDateTime vacReqEndat;

    /** 사용일수 */
    @Column(nullable = false)
    private BigDecimal vacReqUseDay;

    /** 휴가 사유 */
    private String vacReqReason;

    /** 승인 상태 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VacationStatus vacReqStatus;

    /** 처리 사원 ID */
    private Long managerId;

    /** 승인/반려 처리 시간 */
    private LocalDateTime vacReqUpdateAt;

    /** 반려 사유 */
    private String vacReqRejectReason;

    /** 사원 직급 */
    @Column(nullable = false)
    private String reqEmpGrade;

    /** 사원 직책 */
    @Column(nullable = false)
    private String reqEmpTitle;

}
