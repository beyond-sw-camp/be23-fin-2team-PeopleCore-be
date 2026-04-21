package com.peoplecore.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 휴가 결재 문서 생성 이벤트 collab -> hr.
 * hr 가 이 이벤트로 VacationReq insert (PENDING).
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class VacationApprovalDocCreatedEvent {

    private UUID companyId;

    /* collab ApprovalDocument PK */
    private Long approvalDocId;

    /* 기안자 사원 ID */
    private Long empId;

    /* 기안자 이름/부서/직급/직책 스냅샷 (VacationReq 스냅샷 컬럼용) */
    private String empName;
    private Long deptId;
    private String deptName;
    private String empGrade;
    private String empTitle;

    /* 휴가 유형 ID (VacationInfo) */
    private Long infoId;

    /* 휴가 시작/종료 일시 */
    private LocalDateTime vacReqStartat;
    private LocalDateTime vacReqEndat;

    /* 사용일수 (종일 1.0 / 반차 0.5 / 반반차 0.25 / 다일 N.0) */
    private BigDecimal vacReqUseDay;

    /* 휴가 사유 */
    private String vacReqReason;

    /* 최종 결재자 ID (참고용, 알림 필요 시 확장) */
    private Long finalApproverEmpId;

    /* === 이벤트 기반 휴가(출산/배우자출산/유산사산/가족돌봄/공가) 전용 메타 === */

    /* 증빙 파일 URL (MinIO) - 출산/유산사산/배우자출산/공가 시 필수. null 허용 (기존 연차/월차 신청은 미첨부) */
    private String proofFileUrl;

    /* 임신 주수 - 유산·사산휴가(MISCARRIAGE) 시 필수. 주수 기반으로 일수 자동 산정 */
    /*   ≤11주: 5일 / 12~15: 10일 / 16~21: 30일 / 22~27: 60일 / ≥28: 90일 */
    private Integer pregnancyWeeks;

    /* 공가 하위 사유 - 공가(OFFICIAL_LEAVE) 시 필수. OfficialLeaveReason enum 값을 문자열로 전달 */
    /* (RESERVE_FORCES / CIVIL_DEFENSE / CIVIC_DUTY / COURT / HEALTH_CHECK / ETC) */
    private String officialLeaveReason;

    /* 관련 출산일 - 배우자출산(SPOUSE_BIRTH) 시 필수. 출산일 기준 90일 이내 서버 검증 */
    private LocalDate relatedBirthDate;
}
