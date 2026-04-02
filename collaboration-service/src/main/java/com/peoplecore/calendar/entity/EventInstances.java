package com.peoplecore.calendar.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "event_instances")    //개별일정
public class EventInstances {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long eventInstancesId;

//    원래시작일시
    private LocalDateTime originalStart;

//    시작일시 - 수정됐으면 바뀐 값
    private LocalDateTime startAt;

//    종료일시
    private LocalDateTime endAt;

//    개별수정여부
    private Boolean isException;

//    개별취소여부
    private Boolean isCancelled;

//    개별제목
    private String overrideTitle;

//    개별공개여부
    private Boolean overrideIsPublic;

    @Column(nullable = false)
    private UUID companyId;

    @Column(nullable = false)
    private Long eventsId;

    @Column(nullable = false)
    private Long rec_rules_id;

}
