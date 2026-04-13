package com.peoplecore.attendence.entity;

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
                        columnList = "company_id, emp_id, com_rec_date"),
                @Index(name = "idx_commute_emp_date",
                        columnList = "emp_id, com_rec_date")
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
     */
    @Id
    @Column(name = "com_rec_date", nullable = false)
    private LocalDate comRecDate;

    /**
     * 회사 ID
     */
    @Column(nullable = false)
    private UUID companyId;

    /**
     * 사원 아이디
     */
    @Column(nullable = false)
    private Long empId;

    /**
     * 출근 시각
     */
    private LocalDateTime comRecCheckIn;

    /**
     * 퇴근 시각
     */
    private LocalDateTime comRecCheckOut;

    /*출근 체크인 처리
    TODO : null인 상태에서만 세팅 가능하도록 서비스 레이어 에서 검증 필요 (중복 체크인 방지)*/
    public void checkIn(LocalDateTime at) {
        this.comRecCheckOut = at;
    }

    /* 퇴근 체크아웃 처리
     *     TODO : 체크인 이후 시각만 허용하도록 서비스 레이어에서 검증 필요 */
    public void checkOut(LocalDateTime at) {
        this.comRecCheckOut = at;
    }

}
