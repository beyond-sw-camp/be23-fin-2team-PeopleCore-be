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

    /* 출근 체크인 처리 — 시각/IP/초기상태/휴일이유 일괄 세팅 */
    public void checkIn(LocalDateTime at, String ip,
                        WorkStatus initialStatus, HolidayReason reason) {
        if (this.comRecCheckIn != null) {
            throw new IllegalStateException(
                    "이미 체크인된 레코드에 checkIn() 재호출 — comRecId=" + this.comRecId);
        }
        this.comRecCheckIn = at;
        this.checkInIp = ip;
        this.workStatus = initialStatus; // 체크아웃 시 NORMAL/EARLY_LEAVE/LATE_AND_EARLY 로 확정
        this.holidayReason = reason;
    }

    /*
     * 퇴근 체크아웃 처리 — WorkStatus 최종 확정.
     * 가드: 체크인 없이 불가 / 중복 체크아웃 불가 / 체크아웃 < 체크인 불가.
     */
    public void checkOut(LocalDateTime at, String ip, WorkStatus finalStatus) {
        if (this.comRecCheckIn == null) {
            throw new IllegalStateException(
                    "체크인 없이 체크아웃 불가 — comRecId=" + this.comRecId);
        }
        if (this.comRecCheckOut != null) {
            throw new IllegalStateException(
                    "이미 체크아웃된 레코드에 checkOut() 재호출 — comRecId=" + this.comRecId);
        }
        if (at.isBefore(this.comRecCheckIn)) {
            throw new IllegalStateException(
                    "체크아웃 시각이 체크인보다 이전 — comRecId=" + this.comRecId
                            + ", checkIn=" + this.comRecCheckIn + ", checkOut=" + at);
        }
        this.comRecCheckOut = at;
        this.checkOutIp = ip;
        this.workStatus = finalStatus;
    }

    /* 배치 자동마감 — workStatus = AUTO_CLOSED, 모든 근무분 0 */
    public void markAutoClosed(LocalDateTime closedAt) {
        if (this.comRecCheckIn == null) {
            throw new IllegalStateException("체크인 없이 자동마감 불가");
        }
        if (this.comRecCheckOut != null) {
            throw new IllegalStateException("이미 체크아웃된 레코드에 자동마감 불가");
        }
        this.comRecCheckOut = closedAt;
        this.workStatus = WorkStatus.AUTO_CLOSED;
        this.actualWorkMinutes = 0L;
        this.overtimeMinutes = 0L;
        this.unrecognizedOtMinutes = 0L;
        this.recognizedExtendedMinutes = 0L;
        this.recognizedNightMinutes = 0L;
        this.recognizedHolidayMinutes = 0L;
    }

    /* 근태정정 승인 시 workStatus 재설정 */
    public void applyCorrection(WorkStatus correctedStatus) {
        this.workStatus = correctedStatus;
    }

    /* 급여연동 분 컬럼 일괄 갱신 */
    public void applyPayrollMinutes(Long actualWork, Long overtime, Long unrecognizedOt,
                                    Long extended, Long night, Long holiday) {
        if (actualWork == null || overtime == null || unrecognizedOt == null
                || extended == null || night == null || holiday == null) {
            throw new IllegalArgumentException("급여 연동 분 컬럼은 null 허용 안 됨");
        }
        if (unrecognizedOt < 0 || unrecognizedOt > overtime) {
            throw new IllegalArgumentException(
                    "unrecognizedOtMinutes 범위 위반: unrecognizedOt=" + unrecognizedOt
                            + ", overtime=" + overtime);
        }
        long maxTyped = Math.max(extended, Math.max(night, holiday));
        if (overtime < maxTyped) {
            throw new IllegalArgumentException(
                    "overtimeMinutes < max(recognized_*) 불변식 위반: overtime=" + overtime
                            + ", ext=" + extended + ", night=" + night + ", holiday=" + holiday);
        }
        if (actualWork < overtime) {
            throw new IllegalArgumentException(
                    "actualWorkMinutes < overtimeMinutes 불변식 위반: actual=" + actualWork
                            + ", overtime=" + overtime);
        }
        this.actualWorkMinutes = actualWork;
        this.overtimeMinutes = overtime;
        this.unrecognizedOtMinutes = unrecognizedOt;
        this.recognizedExtendedMinutes = extended;
        this.recognizedNightMinutes = night;
        this.recognizedHolidayMinutes = holiday;
    }

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
