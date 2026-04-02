package com.peoplecore.collaboration_service.board.entity;

import com.peoplecore.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;

/**
 * 게시글
 */
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoardPost extends BaseTimeEntity {

    /** 게시글 Id */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long postId;

    /** 게시판  카테고리 ID */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private BoardCategory boardCategory;

    /** 회사 Id */
    @Column(nullable = false)
    private UUID companyId;

    /** 작성자 Id */
    @Column(nullable = false)
    private Long empId;

    /** 작성자 명 */
    @Column(nullable = false)
    private String empName;

    /** 작성자 부서명 */
    @Column(nullable = false)
    private String deptName;

    /** 게시글 제목 */
    @Column(nullable = false)
    private String postTitle;

    /** 게시글 내용 - HTML or md */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String postContents;

    /** 조회수 - default == fasle */
    @Column(nullable = false)
    private Integer postViewCount;

    /** 비밀글 여부 - default == fasle */
    @Column(nullable = false)
    @Builder.Default
    private Boolean postIsSecret = false;

    /** 비밀번호 */
    private String postSecretPassword;

    /** 비공개 여부 - default ==false/ 작성자 + 관리자만 벌수 있음 */
    @Column(nullable = false)
    @Builder.Default
    private Boolean postIsPrivate = false;

    /** 삭제 일시 */
    private LocalDateTime postDeletedAt;

    /** 작성자 직급 */
    @Column(nullable = false)
    private String empGrade;

    /** 잓성자 직책 */
    @Column(nullable = false)
    private String empTitle;

    /** 상단 고정 여부 - default==false */
    @Column(nullable = false)
    @Builder.Default
    private Boolean postIsPinned = false;

}
