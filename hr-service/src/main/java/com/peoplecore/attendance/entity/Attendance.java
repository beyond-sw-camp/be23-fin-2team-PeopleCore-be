package com.peoplecore.attendance.entity;

import com.peoplecore.employee.domain.Employee;
import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 근태(월별 파티션) 테이블 -- 일일 근태 집계/확정본.

 * PK 전략 (Hibernate 6 + MySQL 파티션 호환):
 *  - JPA 매핑은 단일 PK(attenId) 만 사용. @IdClass 를 쓰면 Hibernate 가 auto_increment 컬럼을
 *    PK 뒤로 밀어 MySQL "auto_increment 는 key 첫 컬럼" 제약과 충돌함.
 *  - DB 레벨에서는 CommuteRecordPartitionInitializer 가 ALTER 로 (atten_id, atten_work_date)
 *    복합 PK 로 재정의 → 이 상태에서 RANGE COLUMNS(atten_work_date) 파티션 적용.

 * 비즈니스 유일성:
 *  - UNIQUE(company_id, emp_id, atten_work_date) 로 DB 가 보장. CommuteRecord 와 1:1 매칭.

 * 인덱스:
 *  - (company_id, emp_id, atten_work_date) 대시보드 집계
 *  - (emp_id, atten_work_date) 개인 근태 조회
 */
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(
        name = "attendance",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_attendance_company_emp_date",
                columnNames = {"company_id", "emp_id", "atten_work_date"}
        ),
        indexes = {
                @Index(name = "idx_attendance_company_emp_date",
                        columnList = "company_id, emp_id, atten_work_date"),
                @Index(name = "idx_attendance_emp_date",
                        columnList = "emp_id, atten_work_date")
        }
)
public class Attendance extends BaseTimeEntity {

    /**
     * 근태 ID - JPA 매핑상 단일 PK (AUTO_INCREMENT).
     * DB 레벨에서는 Initializer 가 (atten_id, atten_work_date) 복합 PK 로 재정의 (파티셔닝용).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "atten_id", nullable = false)
    private Long attenId;

    /**
     * 근무 일자 (월별 파티션 키).
     * insert 시 반드시 세팅. JPA 매핑상 @Id 아님 — DB 레벨 복합 PK 의 2번째 컬럼은 Initializer 담당.
     */
    @Column(name = "atten_work_date", nullable = false)
    private LocalDate attenWorkDate;

    /** 회사 ID */
    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    /** 사원 아이디
      */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "emp_id",
            nullable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)
    )
    private Employee employee;
    
    /** 근무 유형  (정상,휴가,휴일,출장 등)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "atten_work_type", nullable = false, length = 30)
    private AttendanceWorkType attenWorkType;

    /** 실 근무 시간 - default == 0 */
    @Column(nullable = false)
    private Integer attenWorkMinute;

    /** 초과 근무 (분) - default == 0 */
    @Column(nullable = false)
    private Integer attenOverMinute;

    /** 지각 시간 (분) - default ==0 */
    @Column(nullable = false)
    private Integer attenLateMinute;

    /** 조퇴시간 (분) - default==0 */
    @Column(nullable = false)
    private Integer attenLeaveMinute;

    /** 근태 상태 - default 확정/ ex) 확정/수정
    */
    @Enumerated(EnumType.STRING)
    @Column(name = "atten_status", nullable = false, length = 20)
    private AttendanceStatus attenStatus;

    /**
     * 근무그룹 스냅샷 (ManyToOne LAZY).
     * 판정 시점의 근무그룹을 고정 기록 → 사원이 나중에 근무그룹 변경해도 과거 판정 유지.
     * MySQL 파티션 테이블 FK 제약 불가 → NO_CONSTRAINT.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "work_group_id",
            nullable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)
    )
    private WorkGroup workGroup;

    /** 낙관적 락 - 정정 반영 시 동시 수정 충돌 방지 */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** 근태 수치 일괄 갱신 (정정 반영 시) */
    public void updateMinutes(int work, int over, int late, int leave) {
        this.attenWorkMinute = work;
        this.attenOverMinute = over;
        this.attenLateMinute = late;
        this.attenLeaveMinute = leave;
    }
    /** 상태 전이 */
    public void changeStatus(AttendanceStatus newStatus) {
        this.attenStatus = newStatus;
    }}


