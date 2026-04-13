package com.peoplecore.attendance.entity;

import com.peoplecore.employee.domain.Employee;
import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import lombok.*;

/**
 * 출퇴근 기록(월별 파티션)
 * comRecDate가 mysql range Colums 파티션 키 -> pk에 반드시 포함되어야 함
 * comRecId 는 Auto_Increment (mysql은 복합 pk의 첫 컬럼에 한해 자동 증가 허용);
 * 파티셔닝 테이블은 ddl-auto로 생성 되지 않음 -> 최초 1회 Alter Table로 적용
 * 인덱스 :
 * company_id, emp_id, com_rec_date) - 회사 범위 내 사원별 조회 대쉬 보드
 * emp_id, com_rec_date - 개인 근태 조회
 */
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(
        name = "commute_record",
        indexes = {
                @Index(name = "idx_commute_company_emp_date",
                        columnList = "company_id, emp_id, work_date"),
                @Index(name = "idx_commute_emp_date",
                        columnList = "emp_id, work_date")
        }
)
@IdClass(CommuteRecordId.class)
public class CommuteRecord extends BaseTimeEntity {

    /**
     * 출퇴근  기록 ID - 복합 PK 1
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "com_rec_id", nullable = false)
    private Long comRecId;

    /**
     * 근무 일자 - 복합 PK 2 & 월별 파티션 키
     * insert 시 반드시 세팅  -> 서비스 레이어에서 LocalDateTime.now 주입
     * 근무 일자 (복합 PK 2 & 월별 파티션 키). 필드명 알파벳 순서상 comRecId 뒤에 오도록 'workDate' 명명.
     */
    @Id
    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    /**
     * 회사 ID
     */
    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    /**
     * 사원 아이디
     * mysql 파티션 테이블은 Fk 제약을 허용하지 않아 No_Constract 지정
     * 참조 무결성은 애플리케이션 (서비스) 에서 보장
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emp_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Employee employee;

    /**
     * 출근 시각
     */
    private LocalDateTime comRecCheckIn;

    /**
     * 퇴근 시각
     */
    private LocalDateTime comRecCheckOut;

    /**
     * 출근 체크인 시 클라이언트 IP.
     * CompanyAllowedIp 매칭 결과에 따라 isOffsite 함께 기록.
     */
    @Column(name = "check_in_ip", length = 45)   // IPv6 최대 길이 대비
    private String checkInIp;

    /**
     * 퇴근 체크아웃 시 클라이언트 IP
     */
    @Column(name = "check_out_ip", length = 45)
    private String checkOutIp;

    /**
     * 근무지 외 체크 여부.
     * 체크인 시점 IP 가 회사 허용 대역에 없으면 true.
     * 대시보드 "근무지 외 근태체크" 카드 집계 기준.
     */
    @Column(name = "is_offsite", nullable = false)
    @Builder.Default
    private Boolean isOffsite = false;

    /*출근 체크인 처리
    TODO : null인 상태에서만 세팅 가능하도록 서비스 레이어 에서 검증 필요 (중복 체크인 방지)*/
    public void checkIn(LocalDateTime at, String ip, boolean offsite) {
        this.comRecCheckIn = at;
        this.checkInIp = ip;
        this.isOffsite = offsite;
    }

    /* 퇴근 체크아웃 처리
     *     TODO : 체크인 이후 시각만 허용하도록 서비스 레이어에서 검증 필요 */
    public void checkOut(LocalDateTime at, String ip) {
        this.comRecCheckOut = at;
        this.checkOutIp = ip;
    }


}
