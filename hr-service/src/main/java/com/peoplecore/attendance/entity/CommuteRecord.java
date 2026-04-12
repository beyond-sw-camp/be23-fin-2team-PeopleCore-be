//package com.peoplecore.attendence.entity;
//
//import com.peoplecore.entity.BaseTimeEntity;
//import jakarta.persistence.*;
//import java.time.LocalDate;
//import java.time.LocalDateTime;
//import java.util.UUID;
//import lombok.*;
//
///**
// * 출퇴근 기록(월별 파티션)
// */
//@Entity
//@Getter
//@NoArgsConstructor
//@AllArgsConstructor
//@Builder
//@IdClass(CommuteRecordId.class)
//public class CommuteRecord extends BaseTimeEntity {
//
//    /** 출퇴근  기록 ID - 복합 PK */
//    @Id
//    private Long comRecId;
//
//    /** 근무 일자 - 복합 PK */
//    @Id
//    private LocalDate comRecDate;
//
//    /** 회사 ID */
//    @Column(nullable = false)
//    private UUID companyId;
//
//    /** 사원 아이디 */
//    @Column(nullable = false)
//    private Long empId;
//
//    /** 출근 시각 */
//    private LocalDateTime comRecCheckIn;
//
//    /** 퇴근 시각 */
//    private LocalDateTime comRecCheckOut;
//
//}
