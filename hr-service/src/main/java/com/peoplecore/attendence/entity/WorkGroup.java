package com.peoplecore.attendence.entity;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import lombok.*;

/**
 * 근무 그룹
 */
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkGroup extends BaseTimeEntity {

    public enum GroupOvertimeRecognize {
        APPROVAL,
        ALL
    }

    /** 근무 그룹 iid */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long workGroupId;

    /** 회사 id */
    @Column(nullable = false)
    private UUID groupCompanyId;

    /** 근무 그룹 명 */
    @Column(nullable = false)
    private String groupName;

    /** 근무 그룹 코드 - unique */
    @Column(nullable = false)
    private String groupCode;

    /** 근무 그룹 설명 */
    @Column(columnDefinition = "TEXT")
    private String groupDesc;

    /** 근무제유형 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private String groupType;

    /** 출근 시간 */
    @Column(nullable = false)
    private LocalTime groupStartTime;

    /** 퇴근 시간 */
    @Column(nullable = false)
    private LocalTime groupEndTime;

    /** 근무 요일 - 비트 마스크(월1,화2,수4,목8,금16,토32,일 64) 기본값 월~금 -> 컨버터 코드 작성 */
    @Column(nullable = false)
    private Integer groupWorkDay;

    /** 휴게 시작 시간 */
    @Column(nullable = false)
    private LocalTime groupBreakStart;

    /** 휴게 종료 시간 */
    @Column(nullable = false)
    private LocalTime groupBreakEnd;

    /** 지동 출퇴근 여부 */
    @Column(nullable = false)
    private Boolean groupAutoCheck;

    /** 초과 근무 인정 방식 - 결제 승인만, 전체 인정 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GroupOvertimeRecognize groupOvertimeRecognize;

    /** 근태 체크 디바이스 */
    @Column(nullable = false)
    private String groupDevice;

    /** 삭제 일시 - null일 경우 활ㄹ성화 */
    private LocalDateTime groupDeleteat;

    /** 생성자 id */
    @Column(nullable = false)
    private Long groupManagerId;

}
