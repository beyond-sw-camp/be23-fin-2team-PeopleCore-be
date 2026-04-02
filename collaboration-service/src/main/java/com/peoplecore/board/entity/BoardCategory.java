package com.peoplecore.board.entity;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

/**
 * 게시판 카테고리
 */
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoardCategory extends BaseTimeEntity {

    /** 게시판  카테고리 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long categoryId;

    /** 회사 ID */
    @Column(nullable = false)
    private UUID componyId;

    /** 카테고리명 */
    @Column(nullable = false)
    private String categoryName;

    /** 카테고리 코드 */
    @Column(nullable = false)
    private String categoryCode;

    /** 설명 */
    private String categoryDescription;

    /** 익명 허용 여부 - default == false */
    @Column(nullable = false)
    @Builder.Default
    private Boolean categoryIsAnonymity = false;

    /** 횔성화 여부 - default == true */
    @Column(nullable = false)
    @Builder.Default
    private Boolean categoryIsActive = true;

    /** 생성자 id - 전사 게시판 같은 경우는 null */
    private UUID categoryEmpId;

}
