package com.peoplecore.approval.repository;

import com.peoplecore.approval.entity.ApprovalForm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApprovalFormRepository extends JpaRepository<ApprovalForm, Long> {

    /* 전체 양식 목록 — 사원용. 활성·현재 버전 + 노출된 폴더 + 미삭제 양식·폴더만 */
    @Query("SELECT f FROM ApprovalForm f " +
            "JOIN FETCH f.folderId folder " +
            "WHERE f.companyId = :companyId " +
            "AND f.isActive = true AND f.isCurrent = true AND f.isDeleted = false " +
            "AND folder.folderIsVisible = true AND folder.isDeleted = false " +
            "ORDER BY f.formSortOrder")
    List<ApprovalForm> findAllWithFolder(@Param("companyId") UUID companyId);

    /* 폴더별 양식 목록 — 사원용. 폴더가 숨김/삭제면 빈 리스트 */
    @Query("SELECT f FROM ApprovalForm f " +
            "JOIN FETCH f.folderId folder " +
            "WHERE f.companyId = :companyId " +
            "AND folder.folderId = :folderId " +
            "AND folder.folderIsVisible = true AND folder.isDeleted = false " +
            "AND f.isActive = true AND f.isCurrent = true AND f.isDeleted = false " +
            "ORDER BY f.formSortOrder")
    List<ApprovalForm> findAllWithFolderByFolderId(
            @Param("companyId") UUID companyId, @Param("folderId") Long folderId);

    /* 관리자용 전체 양식 목록 — isActive 무관, 폴더 visibility 무관 (둘 다 관리자가 봐야 함).
     * isDeleted=true 는 비가역 삭제라 어디서도 안 보임. isCurrent=true 만 */
    @Query("SELECT f FROM ApprovalForm f " +
            "JOIN FETCH f.folderId folder " +
            "WHERE f.companyId = :companyId " +
            "AND f.isCurrent = true AND f.isDeleted = false " +
            "AND folder.isDeleted = false " +
            "ORDER BY f.formSortOrder")
    List<ApprovalForm> findAllForAdmin(@Param("companyId") UUID companyId);

    /* 관리자용 폴더별 양식 목록 — 숨김 폴더라도 통과(관리자), 비활성 양식 포함, 삭제된 것만 제외 */
    @Query("SELECT f FROM ApprovalForm f " +
            "JOIN FETCH f.folderId folder " +
            "WHERE f.companyId = :companyId " +
            "AND folder.folderId = :folderId " +
            "AND folder.isDeleted = false " +
            "AND f.isCurrent = true AND f.isDeleted = false " +
            "ORDER BY f.formSortOrder")
    List<ApprovalForm> findAllForAdminByFolderId(@Param("companyId") UUID companyId,
                                                 @Param("folderId") Long folderId);

    /* 양식 상세 — 현재 버전 단건. 미삭제·활성만 (옛 버전·롤백은 별도 메서드) */
    @Query("SELECT f FROM ApprovalForm f " +
            "JOIN FETCH f.folderId folder " +
            "WHERE f.formId = :formId " +
            "AND f.companyId = :companyId " +
            "AND f.isActive = true AND f.isCurrent = true AND f.isDeleted = false")
    Optional<ApprovalForm> findDetailById(
            @Param("formId") Long formId, @Param("companyId") UUID companyId);

    /* 폴더 내 최대 정렬순서 — 미삭제 양식만 */
    @Query("SELECT COALESCE(MAX(f.formSortOrder), 0) FROM ApprovalForm f " +
            "WHERE f.companyId = :companyId AND f.folderId.folderId = :folderId " +
            "AND f.isDeleted = false")
    Integer findMaxSortOrderInFolder(@Param("companyId") UUID companyId, @Param("folderId") Long folderId);

    /* 양식코드 중복 체크 — 미삭제 현재 버전 기준. 삭제된 코드는 재사용 가능 (isDeleted=true 는 카운트 X).
     * isActive 는 무관 — "사용 안함" 양식도 코드 점유 중으로 간주 */
    @Query("SELECT COUNT(f) > 0 FROM ApprovalForm f " +
            "WHERE f.companyId = :companyId AND f.formCode = :formCode " +
            "AND f.isCurrent = true AND f.isDeleted = false")
    boolean existsActiveByCompanyIdAndFormCode(@Param("companyId") UUID companyId,
                                               @Param("formCode") String formCode);

    /* 양식명 중복 체크 — 동상 (삭제된 이름 재사용 가능) */
    @Query("SELECT COUNT(f) > 0 FROM ApprovalForm f " +
            "WHERE f.companyId = :companyId AND f.formName = :formName " +
            "AND f.isCurrent = true AND f.isDeleted = false")
    boolean existsActiveByCompanyIdAndFormName(@Param("companyId") UUID companyId,
                                               @Param("formName") String formName);

    /* 여러 양식 ID 일괄 조회 (일괄 설정/순서변경용) — 미삭제 현재 버전 */
    @Query("SELECT f FROM ApprovalForm f " +
            "WHERE f.companyId = :companyId AND f.formId IN :formIds " +
            "AND f.isCurrent = true AND f.isDeleted = false")
    List<ApprovalForm> findAllByCompanyIdAndFormIds(@Param("companyId") UUID companyId,
                                                    @Param("formIds") List<Long> formIds);

    /* 양식 정렬 순서 변경 벌크쿼리 */
    @Modifying
    @Query("UPDATE ApprovalForm f SET f.formSortOrder = :sortOrder " +
            "WHERE f.formId = :formId AND f.companyId = :companyId")
    void updateSortOrder(@Param("companyId") UUID companyId,
                         @Param("formId") Long formId,
                         @Param("sortOrder") Integer sortOrder);

    /* formCode 로 활성+현재+미삭제 양식 단건 조회 — hr-service REST 연동용 */
    @Query("SELECT f FROM ApprovalForm f " +
            "WHERE f.companyId = :companyId AND f.formCode = :formCode " +
            "AND f.isActive = true AND f.isCurrent = true AND f.isDeleted = false")
    Optional<ApprovalForm> findActiveByCompanyIdAndFormCode(@Param("companyId") UUID companyId,
                                                            @Param("formCode") String formCode);

    /* 회사 초기화 시 양식 ensure 패턴용 — 미삭제 current 양식만 */
    @Query("SELECT f FROM ApprovalForm f " +
            "WHERE f.companyId = :companyId AND f.formName = :formName " +
            "AND f.isCurrent = true AND f.isDeleted = false")
    Optional<ApprovalForm> findCurrentByCompanyIdAndFormName(@Param("companyId") UUID companyId,
                                                             @Param("formName") String formName);

    /* 같은 formCode 그룹의 MAX(formVersion) — 새 버전 row INSERT 시 +1 사용.
     * 삭제된 row 도 포함 — uniqueConstraint(company_id, form_code, form_version) 충돌 방지 위해
     * 삭제된 코드 재사용 시에도 버전 번호 이어서 부여 */
    @Query("SELECT COALESCE(MAX(f.formVersion), 0) FROM ApprovalForm f " +
            "WHERE f.companyId = :companyId AND f.formCode = :formCode")
    Integer findMaxVersionByFormCode(@Param("companyId") UUID companyId,
                                     @Param("formCode") String formCode);

    /* 같은 formCode 의 모든 버전 이력 — 관리자 버전 이력 화면. 미삭제만 */
    @Query("SELECT f FROM ApprovalForm f " +
            "WHERE f.companyId = :companyId AND f.formCode = :formCode " +
            "AND f.isDeleted = false " +
            "ORDER BY f.formVersion DESC")
    List<ApprovalForm> findAllVersionsByFormCode(@Param("companyId") UUID companyId,
                                                 @Param("formCode") String formCode);

    /* 롤백 타깃 단건 조회 — 옛 버전(isCurrent 무관) 가져와야 하므로 isCurrent 필터 X.
     * isActive=true AND isDeleted=false 인 양식만 (사용 안함·삭제된 양식은 롤백 불가) */
    @Query("SELECT f FROM ApprovalForm f " +
            "WHERE f.formId = :formId AND f.companyId = :companyId " +
            "AND f.isActive = true AND f.isDeleted = false")
    Optional<ApprovalForm> findByFormIdAndCompanyIdForRollback(@Param("formId") Long formId,
                                                               @Param("companyId") UUID companyId);

    /* 같은 formCode 의 모든 버전 일괄 soft delete — deleteForm 에서 사용. 비가역 */
    @Modifying
    @Query("UPDATE ApprovalForm f SET f.isDeleted = true " +
            "WHERE f.companyId = :companyId AND f.formCode = :formCode " +
            "AND f.isDeleted = false")
    int softDeleteAllVersionsByFormCode(@Param("companyId") UUID companyId,
                                        @Param("formCode") String formCode);
}
