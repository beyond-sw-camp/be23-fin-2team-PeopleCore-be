package com.peoplecore.collaboration_service.common.entity;

import com.peoplecore.common.entity.BaseTimeEntity;
import jakarta.persistence.*;

import java.util.UUID;

import lombok.*;

/**
 * 공통 즐겨찾기
 */
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommonBookmark extends BaseTimeEntity {

    /**
     * 즐겨찾기 Id
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long bookmarkId;

    /**
     * 회사 Id
     */
    @Column(nullable = false)
    private UUID companyId;

    /**
     * 대상 구분
     */
    @Column(nullable = false)
    private String bookmarkEntityType;

    /**
     * 대상 구분 id
     */
    @Column(nullable = false)
    private Long bookmarkEntityId;

    /**
     * 사원 Id
     */
    @Column(nullable = false)
    private Long empId;

    /**
     * 사원 이름
     */
    @Column(nullable = false)
    private String empName;

    /**
     * 사원 부서
     */
    @Column(nullable = false)
    private String empDeptName;

    //    사원 직책
    @Column(nullable = false)
    private String empTitle;

//    사원 직급
    @Column(nullable = false)
    private String empGrade;

}
