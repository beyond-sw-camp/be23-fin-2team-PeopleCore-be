package com.peoplecore.common.entity;

import com.peoplecore.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;

/**
 * 공통 알림
 */
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommonAlarm extends BaseTimeEntity {

    /** 공통 알람 id */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long alarmId;

    /** 회사 id */
    @Column(nullable = false)
    private UUID companyId;

    /** 수신자 사원 id */
    @Column(nullable = false)
    private Long alarmEmpId;

    /** 알림 유형 */
    @Column(nullable = false, length = 50)
    private String alarmType;

    /** 알림 내용 */
    @Column(nullable = false)
    private String alarmContent;

    /** 참조 대상 구분 */
    private String alarmRefType;

    /** 참조 대상 id */
    private Long alarmRefId;

    /** 읽음 여부 */
    @Column(nullable = false)
    private Boolean alarmIsRead;

    /** 읽음 일시 */
    private LocalDateTime alarmUpdateAt;

}
