package com.peoplecore.filevault.entity;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * 파일함 활동 이력.
 *
 * <p>폴더/파일 생성·삭제·업로드·다운로드·이름변경·이동·복원 등
 * 사용자 행동을 영구 기록한다. 우측 활동 이력 패널 및 감사 로그용.</p>
 */
@Entity
@Table(
    name = "file_vault_activity",
    indexes = {
        @Index(name = "idx_activity_company_created", columnList = "company_id,created_at"),
        @Index(name = "idx_activity_emp_created", columnList = "emp_id,created_at")
    }
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileVaultActivity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "activity_id")
    private Long id;

    /**
     * 회사 id (테넌트 격리).
     */
    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    /**
     * 행위자 empId.
     */
    @Column(name = "emp_id", nullable = false)
    private Long empId;

    /**
     * 행위자 이름 스냅샷 (조회 시 조인 비용 절감).
     */
    @Column(name = "user_name", nullable = false, length = 100)
    private String userName;

    /**
     * 행동 유형 ({@link ActivityAction}).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 32)
    private ActivityAction action;

    /**
     * 대상 폴더/파일 이름 스냅샷.
     */
    @Column(name = "target_name", nullable = false, length = 255)
    private String targetName;

    /**
     * 위치(부모 폴더 이름) 스냅샷.
     */
    @Column(name = "location", nullable = false, length = 255)
    private String location;
}
