package com.peoplecore.attendence.entity;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;

/**
 * 근태수정요청
 */
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendenceModify extends BaseTimeEntity {

    /** 근태수정요청 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long attenModiId;

    /** 회사 ID */
    @Column(nullable = false)
    private UUID companyId;

    /** 요청 사원 아이디 */
    @Column(nullable = false)
    private Long empId;

    /** 근태 ID */
    @Column(nullable = false)
    private Long attenId;

    /** 근무 일자 */
    @Column(nullable = false)
    private LocalDate attenWorkDate;

    /** 요청 사원 이름 */
    @Column(nullable = false)
    private String attenEmpName;

    /** 요청 사원 부서 */
    @Column(nullable = false)
    private String attenEmpDeptName;

    /** 요청 출근 시간 */
    private LocalDateTime attenReqCheckIn;

    /** 요청 퇴근 시간 */
    private LocalDateTime attenReqCheckOut;

    /** 수정 사유 */
    @Column(nullable = false)
    private String attenReason;

    /** 수정 처리 상태 - 인사과 등록/대기,승인,반려 default==대기 */
    @Column(nullable = false)
    private String attenStatus;

    /** 처리자 id */
    private Long attenManagerId;

    /** 요청 사원 직급 */
    @Column(nullable = false)
    private String attenEmpGrade;

    /** 요청 사원 직책 */
    @Column(nullable = false)
    private String attenEmpTitle;

}
