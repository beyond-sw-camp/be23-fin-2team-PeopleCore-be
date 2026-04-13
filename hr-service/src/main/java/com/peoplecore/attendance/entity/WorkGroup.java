package com.peoplecore.attendance.entity;

import com.peoplecore.attendance.dto.WorkGroupReqDto;
import com.peoplecore.company.domain.Company;
import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.time.LocalTime;

import lombok.*;

/**
 * 근무 그룹
 */
@Table(
        uniqueConstraints = @UniqueConstraint(
                name = "uk_work_group_company_code",
                columnNames = {"company_id", "group_code"}
        )
)
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

    /**
     * 근무 그룹 id
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long workGroupId;

    /**
     * 회사 id
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    /**
     * 근무 그룹 명
     */
    @Column(nullable = false)
    private String groupName;

    /**
     * 근무 그룹 코드 - unique
     */
    @Column(nullable = false)
    private String groupCode;

    /**
     * 근무 그룹 설명
     */
    @Column(columnDefinition = "TEXT")
    private String groupDesc;

    /**
     * 출근 시간
     */
    @Column(nullable = false)
    private LocalTime groupStartTime;

    /**
     * 퇴근 시간
     */
    @Column(nullable = false)
    private LocalTime groupEndTime;

    /**
     * 근무 요일 - 비트 마스크(월1,화2,수4,목8,금16,토32,일 64) 기본값 월~금 -> 컨버터 코드 작성
     */
    @Column(nullable = false)
    private Integer groupWorkDay;

    /**
     * 휴게 시작 시간
     */
    @Column(nullable = false)
    private LocalTime groupBreakStart;

    /**
     * 휴게 종료 시간
     */
    @Column(nullable = false)
    private LocalTime groupBreakEnd;

    /**
     * 초과 근무 인정 방식 - 결제 승인만, 전체 인정
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GroupOvertimeRecognize groupOvertimeRecognize;

    /**
     * 모바일 출퇴근 허용 여부
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean groupMobileCheck = false;

    /** 삭제 일시 - null일 경우 활성화 */
    private LocalDateTime groupDeleteAt;

    /**
     * 생성자 id
     */
    private Long groupManagerId;
    /*생성자 이름 */
    private String groupManagerName;


    public void softDelete() {
        this.groupDeleteAt = LocalDateTime.now();
        this.groupCode = this.groupCode + "_DELETED_" + System.currentTimeMillis();

    }

    public void update(WorkGroupReqDto dto) {
        this.groupName = dto.getGroupName();
        this.groupDesc = dto.getGroupDesc();
        this.groupStartTime = dto.getGroupStartTime();
        this.groupEndTime = dto.getGroupEndTime();
        this.groupWorkDay = dto.getGroupWorkDay();
        this.groupBreakStart = dto.getGroupBreakStart();
        this.groupBreakEnd = dto.getGroupBreakEnd();
        this.groupOvertimeRecognize = dto.getGroupOvertimeRecognize();
        this.groupMobileCheck = dto.getGroupMobileCheck();
    }
}
