package com.peoplecore.entity;

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

    /**
     * 공통 알람 id
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long alarmId;

    /**
     * 회사 id
     */
    @Column(nullable = false)
    private UUID companyId;

    /**
     * 수신자 사원 id
     */
    @Column(nullable = false)
    private Long alarmEmpId;

    /**
     * 알림 유형
     */
    @Column(nullable = false, length = 50)
    private String alarmType;

    /*알람 제목 */
    @Column(nullable = false, length = 500)
    private String alarmTitle;

    /**
     * 알림 내용
     */
    @Column(length = 1000)
    private String alarmContent;
    /*알람 주소*/
    @Column(length = 200)
    private String alarmLink;

    /**
     * 참조 대상 구분
     */
    private String alarmRefType;

    /**
     * 참조 대상 id
     */
    private Long alarmRefId;

    /**
     * 읽음 여부
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean alarmIsRead = false;


    public void markAsRead() {
        this.alarmIsRead = true;
    }
}
