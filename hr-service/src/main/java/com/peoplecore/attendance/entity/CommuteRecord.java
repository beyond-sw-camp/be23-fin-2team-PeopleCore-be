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
 *
 * PK 전략 (Hibernate 6 + MySQL 파티션 호환):
 *  - JPA 매핑은 단일 PK(comRecId) 만 사용. @IdClass 를 쓰면 Hibernate 가 auto_increment 컬럼을
 *    PK 뒤로 밀어 MySQL "auto_increment 는 key 첫 컬럼" 제약과 충돌함.
 *  - DB 레벨에서는 CommuteRecordPartitionInitializer 가 ALTER 로 (com_rec_id, work_date)
 *    복합 PK 로 재정의 → 이 상태에서 RANGE COLUMNS(work_date) 파티션 적용.
 *
 * 비즈니스 유일성:
 *  - UNIQUE(company_id, emp_id, work_date) — 중복 체크인/race condition 차단.
 *
 * 인덱스:
 *  - (company_id, emp_id, work_date) 회사 범위 사원별 조회
 *  - (emp_id, work_date) 개인 근태 조회
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

    /**
     * 출퇴근 기록 ID - JPA 매핑상 단일 PK (AUTO_INCREMENT).
     * DB 레벨에서는 Initializer 가 (com_rec_id, work_date) 복합 PK 로 재정의 (파티셔닝용).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "com_rec_id", nullable = false)
    private Long comRecId;

    /**
     * 근무 일자 (월별 파티션 키).
     * insert 시 반드시 세팅 → 서비스 레이어에서 LocalDate.now() 주입.
     * JPA 매핑상 @Id 아님 — DB 레벨 복합 PK 의 2번째 컬럼은 Initializer 담당.
     */
    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    /*
     * 회사 ID
     */
    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    /*
     * 사원 아이디
     * mysql 파티션 테이블은 Fk 제약을 허용하지 않아 No_Constract 지정
     * 참조 무결성은 애플리케이션 (서비스) 에서 보장
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emp_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Employee employee;

    /*
     * 출근 시각
     */
    private LocalDateTime comRecCheckIn;

    /*
     * 퇴근 시각
     */
    private LocalDateTime comRecCheckOut;

    /*
     * 출근 체크인 시 클라이언트 IP.
     * CompanyAllowedIp 매칭 결과에 따라 isOffsite 함께 기록.
     */
    @Column(name = "check_in_ip", length = 45)   // IPv6 최대 길이 대비
    private String checkInIp;

    /*
     * 퇴근 체크아웃 시 클라이언트 IP
     */
    @Column(name = "check_out_ip", length = 45)
    private String checkOutIp;

    /*
     * 근무지 외 체크 여부.
     * 체크인 시점 IP 가 회사 허용 대역에 없으면 true.
     * 대시보드 "근무지 외 근태체크" 카드 집계 기준.
     */
    @Column(name = "is_offsite", nullable = false)
    @Builder.Default
    private Boolean isOffsite = false;

    /*체크인 상태 (ON_TIME/LATE/HOLIDAY_WORK) */
    @Enumerated(EnumType.STRING)
    @Column(name = "check_in_status", length = 20)
    private CheckInStatus checkInStatus;


    /* 체크아웃 상태 (EARLY_LEAVE/ON_TIME/HOLIDAY_WORK_END). 체크아웃 전엔 null */
    @Enumerated(EnumType.STRING)
    @Column(name = "check_out_status", length = 20)
    private CheckOutStatus checkOutStatus;

    /* 휴일 이유 (NATIONAL/COMPANY/WEEKLY_OFF). 평일이면 null */
    @Enumerated(EnumType.STRING)
    @Column(name = "holiday_reason", length = 20)
    private HolidayReason holidayReason;

    /* 출근 체크인 처리 - 시각/IP/offsite/상태/휴일이유 일괄 세팅 */
    public void checkIn(LocalDateTime at, String ip, boolean offsite,
                        CheckInStatus status, HolidayReason reason) {
        if (this.comRecCheckIn != null) {
            throw new IllegalStateException(
                    "이미 체크인된 레코드에 checkIn() 재호출 - comRecId=" + this.comRecId);
        }
        this.comRecCheckIn = at;
        this.checkInIp = ip;
        this.isOffsite = offsite;
        this.checkInStatus = status;
        this.holidayReason = reason;
    }


    /* 퇴근 체크아웃 처리 - 시각/IP/상태 일괄 세팅
    * 가드 : 체크인 없이 호출 붉가
    * 이미 체크아웃된 레코드에 재호출 불가
    *
    * 지정 넘겨서 퇴근 찍을 시 허용 */
    public void checkOut(LocalDateTime at, String ip, CheckOutStatus status) {
        if (this.comRecCheckIn == null) {
            throw new IllegalStateException(
                    "체크인 없이 체크아웃 불가 - comRecId=" + this.comRecId);
        }
        if (this.comRecCheckOut != null) {
            throw new IllegalStateException(
                    "이미 체크아웃된 레코드에 checkOut() 재호출 - comRecId=" + this.comRecId);
        }
        if (at.isBefore(this.comRecCheckIn)) {
            throw new IllegalStateException(
                    "체크아웃 시각이 체크인보다 이전 - comRecId=" + this.comRecId +
                            ", checkIn=" + this.comRecCheckIn + ", checkOut=" + at);
        }
        this.comRecCheckOut = at;
        this.checkOutIp = ip;
        this.checkOutStatus = status;
    }
}
