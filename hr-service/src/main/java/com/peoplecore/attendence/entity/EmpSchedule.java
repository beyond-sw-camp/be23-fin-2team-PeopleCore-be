package com.peoplecore.attendence.entity;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;
import lombok.*;

/**
 * 사원 스케줄 배정
 */
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmpSchedule extends BaseTimeEntity {

    /** 스케줄 배정 id */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long empScheId;

    /** 회사 ID */
    @Column(nullable = false)
    private UUID companyId;

    /** 사원 아이디 */
    @Column(nullable = false)
    private Long empId;

    /** 근무 스케줄 Id */
    @Column(nullable = false)
    private Long scheId;

    /** 계약 근무분 - 인사과 설정. 사원병 실제 근무시간 (계약서에 적힌대러) */
    @Column(nullable = false)
    private Integer empScheContractedMinute;

    /** 적용 시간 - 인사과 설정ㅇ */
    @Column(nullable = false)
    private LocalDate empScheStartDate;

    /** 적용 종료 시간 - 사실상 update time/null일 경우 적용중 */
    private LocalDate empScheEndDate;

    /** 처리자 id - 인사과 설정 */
    @Column(nullable = false)
    private Long empScheManagerId;

    /** 근무시간 변경 사유 */
    private String empScheReason;

    /** 연봉계약id */
    @Column(nullable = false)
    private Long contractId;

}
