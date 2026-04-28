package com.peoplecore.approval.entity;

import com.peoplecore.entity.BaseTimeEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.*;

/**
 * 결재 문서
 */
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(uniqueConstraints = {
        @UniqueConstraint(columnNames = {"company_id", "doc_num"}),
        @UniqueConstraint(name = "uk_approval_doc_hr_ref", columnNames = {"company_id", "hr_ref_type", "hr_ref_id"})})
public class ApprovalDocument extends BaseTimeEntity {

    /**
     * 결재 문서 Id
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long docId;

    /**
     * 회사 Id
     */
    @Column(nullable = false)
    private UUID companyId;

    /*
     * 결재 번호 - 제출 시 채번
     */
    private String docNum;

    /**
     * 결재 양식 id
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "form_id", nullable = false)
    private ApprovalForm formId;

    /**
     * 기안자 id - 사원 Id
     */
    @Column(nullable = false)
    private Long empId;

    /**
     * 기안자 이름
     */
    @Column(nullable = false)
    private String empName;

    /**
     * 기안자 부서 ID
     */
    private Long empDeptId;

    /**
     * 기안자 부서
     */
    @Column(nullable = false)
    private String empDeptName;

    /**
     * 기안자 직급
     */
    @Column(nullable = false)
    private String empGrade;

    /**
     * 기안자 직책
     */
    @Column(nullable = false)
    private String empTitle;

    /**
     * 양식 유형
     */
    @Column(nullable = false)
    private String docType;


    /*기안 의견 */
    private String docOpinion;

    /**
     * 양식 데이터 - 양식 입력값이 JSON
     */
    @Column(nullable = false, columnDefinition = "json")
    private String docData;

    /**
     * 제목
     */
    @Column(nullable = false)
    private String docTitle;

    /**
     * 문서 상태 - 임시저장,결재중,승인,반려,대기 등등
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ApprovalStatus approvalStatus = ApprovalStatus.PENDING;

    /**
     * 긴급 여부 - default == false
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isEmergency = false;

    /**
     * 제출 일시 - 채번이 새겨지는 시점
     */
    private LocalDateTime docSubmittedAt;

    /**
     * 상태 완료 일시 - 승인/반려 상태 확정 시
     */
    private LocalDateTime docCompleteAt;

    /**
     * 보존 연한 스냅샷 - 결재 완료 시점의 양식 연한을 박제
     * null = 미완결 / 값 존재 = docCompleteAt + retentionYearSnapshot 이 만료일
     * 양식 연한 변경해도 기존 문서는 영향 없음 (정책 B - 소급 적용 X)
     */
    private Integer retentionYearSnapshot;

    @Version
    private Long version;

    /**
     * 이전 문서 Id - 재기안으로 생성된 경우 원본 REJECTED 문서의 docId
     * 최초 기안은 null, 재기안 시 원본 docId 세팅 → 체인 추적(링크드 리스트)
     */
    private Long previousDocId;

    /*개인 문서함 id */
    private Long personalFolderId;

    /*부서 문서함 id */
    private Long deptFolderId;

    /*결재 완성 시 저장되는 url*/
    private String docUrl;

//    멱등성/실패 방어 컬럼 -> unique 제약
    /** 외부(hr-service) 상신 원천 구분자 — "PAYROLL_RUN" / "SEVERANCE" / null(프론트 직접 상신) */
    @Column(length = 20)
    private String hrRefType;

    /** 외부 원천 식별자 — PAYROLL_RUN → payrollRunId, SEVERANCE → sevId */
    private Long hrRefId;



    public void changeStatus(ApprovalStatus approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    /* 결재 완료 - 완료 시점 + 양식 보존연한 박제 (정책 B 소급 X) */
    /* formId 는 LAZY 지만 트랜잭션 내 호출이라 proxy 초기화 안전 */
    public void complete() {
        this.docCompleteAt = LocalDateTime.now();
        this.retentionYearSnapshot = (this.formId != null) ? this.formId.getFormRetentionYear() : null;
    }

    public void markSubmitted() {
        this.docSubmittedAt = LocalDateTime.now();
    }

    public void submit() {
        this.approvalStatus.getState().submit(this);
    }

    public void approve() {
        this.approvalStatus.getState().approve(this);
    }

    public void reject() {
        this.approvalStatus.getState().reject(this);
    }

    public void recall() {
        this.approvalStatus.getState().recall(this);
    }

    /* 결재 처리(승인/반려/회수) 진입 가드 — PENDING 외 상태는 throw */
    public void requireOpenForApproval() {
        this.approvalStatus.getState().ensureOpenForApproval();
    }

    /* 재기안 진입 가드 — REJECTED 외 상태는 throw */
    public void requireResubmittable() {
        this.approvalStatus.getState().ensureResubmittable();
    }

    /* 임시저장 단계 작업(수정/삭제/상신) 진입 가드 — DRAFT 외 상태는 throw */
    public void requireDraftStage() {
        this.approvalStatus.getState().ensureDraftStage();
    }

    /*임시 저장 문서 수정 */
    public void updateDraft(String docTitle, String docData, Boolean isEmergency) {
        this.docTitle = docTitle;
        this.docData = docData;

        if (isEmergency != null) {
            this.isEmergency = isEmergency;
        }
    }

    /*채번 부여*/
    public void assignDocNum(String docNum) {
        this.docNum = docNum;
    }

    /*개인 문서함 배정*/
    public void assignPersonalFolder(Long personalFolderId) {
        this.personalFolderId = personalFolderId;
    }

    /*부서 문서함 배정*/
    public void assignDeptFolder(Long deptFolderId) {
        this.deptFolderId = deptFolderId;
    }

    /*완성 문서 URL 저장*/
    public void assignDocUrl(String docUrl) {
        this.docUrl = docUrl;
    }

}
