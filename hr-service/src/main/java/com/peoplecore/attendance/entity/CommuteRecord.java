package com.peoplecore.attendance.entity;

import com.peoplecore.employee.domain.Employee;
import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 출퇴근 기록 (월별 파티션).
 * 비즈니스 유일성:
 * - UNIQUE(company_id, emp_id, work_date) — 중복 체크인/race condition 차단.
 * 인덱스:
 * - (company_id, emp_id, work_date) 회사 범위 사원별 조회
 * - (emp_id, work_date) 개인 근태 조회
 *
 * TODO 고도화:
 *   - EKS 컨테이너 TZ=UTC 시절에 적재된 work_date 점검 필요.
 *     KST 자정~09:00 사이 체크인 기록이 전날 파티션에 잘못 들어갔을 수 있음
 *     (예: KST 5/8 01:00 → UTC 5/7 16:00 → workDate=2026-05-07 로 적재).
 *     comRecCheckIn 시각과 workDate 가 다른 일자로 어긋난 행을 카운트 후 마이그레이션.
 */
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(
        name = "commute_record",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_commute_company_emp_date",
                columnNames = {"company_id", "emp_id", "work_date"}
        ),
        indexes = {
                @Index(name = "idx_commute_company_emp_date",
                        columnList = "company_id, emp_id, work_date"),
                @Index(name = "idx_commute_emp_date",
                        columnList = "emp_id, work_date")
        }
)
public class CommuteRecord extends BaseTimeEntity {

    /*
     * 출퇴근 기록 ID — JPA 매핑상 단일 PK (AUTO_INCREMENT).
     * DB 레벨에서는 Initializer 가 (com_rec_id, work_date) 복합 PK 로 재정의 (파티셔닝용).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long comRecId;

    /*
     * 근무 일자 (월별 파티션 키).
     * insert 시 반드시 세팅 → 서비스 레이어에서 LocalDate.now() 주입.
     */
    @Column(nullable = false)
    private LocalDate workDate;

    /* 회사 ID */
    @Column(nullable = false)
    private UUID companyId;

    /*
     * 사원
     * MySQL 파티션 테이블은 FK 제약 불허 → NO_CONSTRAINT.
     * 참조 무결성은 서비스에서 보장.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emp_id", nullable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Employee employee;

    /* 출근 시각 — ABSENT 행은 null */
    private LocalDateTime comRecCheckIn;

    /* 퇴근 시각 — ABSENT / 퇴근 미체크 시 null */
    private LocalDateTime comRecCheckOut;

    /* 출근 체크인 IP (IPv6 대비 45자) — ABSENT 행은 null */
    @Column(length = 45)
    private String checkInIp;

    /* 퇴근 체크아웃 IP — ABSENT / 퇴근 미체크 행은 null */
    @Column(length = 45)
    private String checkOutIp;

    /* 휴일 이유 (NATIONAL/COMPANY/WEEKLY_OFF) — 평일 출근이면 null */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private HolidayReason holidayReason;

    /*
     * 하루 최종 근태 상태.
     * 체크인 시 초기값(NORMAL/LATE/HOLIDAY_WORK) 설정 →
     * 체크아웃 시 최종값(NORMAL/LATE/EARLY_LEAVE/LATE_AND_EARLY) 확정 →
     * 배치 시 AUTO_CLOSED 또는 ABSENT 처리.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkStatus workStatus;

    /*
     * 실 근무 분 (휴게시간 차감 완료) — 급여 연동 컬럼.
     * 계산: (checkOut - checkIn) - 휴게구간.
     * ABSENT / AUTO_CLOSED 행은 0.
     */
    @Column(nullable = false)
    @Builder.Default
    private Long actualWorkMinutes = 0L;

    /*
     * 총 초과 분 — 관리자 지표 기준값.
     * 계산: max(0, checkOut - groupEndTime).
     */
    @Column(nullable = false)
    @Builder.Default
    private Long overtimeMinutes = 0L;

    /*
     * 미인정 초과근무 분.
     * 계산: overtimeMinutes - recognizedExtendedMinutes.
     * 대시보드/급여 리포트 집계용.
     */
    @Column(nullable = false)
    @Builder.Default
    private Long unrecognizedOtMinutes = 0L;

    /*
     * 인정된 연장수당 분.
     * APPROVED OvertimeRequest 구간 ∩ 정시 초과 구간.
     */
    @Column(nullable = false)
    @Builder.Default
    private Long recognizedExtendedMinutes = 0L;

    /*
     * 인정된 야간수당 분 (22:00~06:00 구간 ∩ 인정 구간).
     * 연장수당과 중복 카운트 가능 (근기법 가산수당 중복).
     */
    @Column(nullable = false)
    @Builder.Default
    private Long recognizedNightMinutes = 0L;

    /*
     * 인정된 휴일수당 분.
     * 휴일 근무 구간 중 인정된 시간.
     */
    @Column(nullable = false)
    @Builder.Default
    private Long recognizedHolidayMinutes = 0L;

    /* 상태 변경 메서드는 모두 제거됨 - 파티션 테이블이라 모든 UPDATE 는 native UPDATE (work_date 포함) 경유.
     * 호출처:
     *  - 체크인: CommuteService.checkIn (builder + saveAndFlush 로 INSERT)
     *  - 체크아웃: CommuteService.checkOut → CommuteRecordRepository.applyCheckOut
     *  - 자동마감: AutoCloseBatchService → CommuteRecordRepository.applyAutoClose
     *  - 정정 승인: AttendanceModifyService → CommuteRecordRepository.applyAttendanceModify
     *  - 분 컬럼 재계산: PayrollMinutesCalculator → CommuteRecordRepository.applyPayrollMinutes */

    /*
     * 결근 행 생성 팩토리 메서드.
     * 배치가 호출 — comRecCheckIn/Out = null, 모든 근무분 = 0.
     */
    public static CommuteRecord absent(Employee employee, LocalDate workDate, UUID companyId) {
        return CommuteRecord.builder()
                .employee(employee)
                .workDate(workDate)
                .companyId(companyId)
                .workStatus(WorkStatus.ABSENT)
                .build();
    }
}
