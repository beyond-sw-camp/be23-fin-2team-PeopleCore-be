package com.peoplecore.approval.repository;

import com.peoplecore.approval.dto.DocumentCountResponse;
import com.peoplecore.approval.dto.DocumentListResponseDto;
import com.peoplecore.approval.dto.DocumentListSearchDto;
import com.peoplecore.approval.entity.*;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class ApprovalDocumentCustomRepositoryImpl implements ApprovalDocumentCustomRepository {

    private final JPAQueryFactory jpaQueryFactory;

    /*Q클래스 선언 - 엔티티에 QueryDsl전용 객체 */
    private final QApprovalDocument doc = QApprovalDocument.approvalDocument;
    private final QApprovalLine line = QApprovalLine.approvalLine;
    private final QApprovalForm form = QApprovalForm.approvalForm;
    private final QApprovalAttachment attachment = QApprovalAttachment.approvalAttachment;
    private final QPersonalFolderDocument folderDoc = QPersonalFolderDocument.personalFolderDocument;

    @Autowired
    public ApprovalDocumentCustomRepositoryImpl(JPAQueryFactory jpaQueryFactory) {
        this.jpaQueryFactory = jpaQueryFactory;
    }

    /*헬퍼 1 : 공통 검색/필터  조건빌더
     * 모든 문서함에서 공통으로 쓰이는 검색 조건을 booleanBuilder에 추가
     * BooleanBuilder : querydsl 에서 제공하는 동적 쿼리 조건을 조립하는 크래스 , where절의 조건을 프로그래밍으로 조합
     * companyId 필수와 search,기간 양식 , 상태는 값이 있을 때만 조건 추가 */
    private BooleanBuilder applyCommonFilters(UUID companyId, DocumentListSearchDto searchDto) {
        BooleanBuilder builder = new BooleanBuilder();

        /*회사 필터 필수 */
        builder.and(doc.companyId.eq(companyId));

        if (searchDto == null) return builder;

        /*텍스트 검색 : 제목, 기안자명, 문서번호 */
        if (searchDto.getSearch() != null && !searchDto.getSearch().isBlank()) {
            builder.and(
                    /*containsIgnoreCase -> 대소문자 무시 부분 문자열을 검색*/
                    doc.docTitle.containsIgnoreCase(searchDto.getSearch()).or(doc.empName.containsIgnoreCase(searchDto.getSearch()).or(doc.docNum.containsIgnoreCase(searchDto.getSearch())))
            );
        }

        /*시작일 */
        if (searchDto.getStartDate() != null) {
            builder.and(doc.createdAt.goe(searchDto.getStartDate().atStartOfDay()));
        }

        /*종료일 */
        if (searchDto.getEndDate() != null) {
            builder.and(doc.createdAt.lt(searchDto.getEndDate().plusDays(1).atStartOfDay()));
        }

        /*양식 필터 */
        if (searchDto.getFormId() != null) {
            builder.and(doc.formId.formId.eq(searchDto.getFormId()));
        }

        /*상태 필터 */
        if (searchDto.getStatus() != null && !searchDto.getStatus().isBlank()) {
            builder.and(doc.approvalStatus.eq(ApprovalStatus.valueOf(searchDto.getStatus())));
        }

        /* 보존연한 만료 필터 (정책 B - 소급 X) */
        builder.and(notExpired());

        return builder;
    }

    /* 보존연한 미만료 조건 - 미완결(snapshot=NULL) 통과, 완료분은 docCompleteAt + snapshot > 현재 */
    /* HQL 은 INTERVAL <expr> YEAR 미지원 → Hibernate 표준 function timestampadd 사용 (year 단위 정수 더하기) */
    private BooleanExpression notExpired() {
        return doc.retentionYearSnapshot.isNull().or(
                Expressions.booleanTemplate(
                        "function('timestampadd', year, {1}, {0}) > current_timestamp",
                        doc.docCompleteAt, doc.retentionYearSnapshot
                )
        );
    }

    /*헬퍼 2 : 첨부파일 존재 여부 서브쿼리
    * select exists (select 1 from attachment where attachment.docId = doc.doc_id
    * true/false 반환, projection에서 hasAttachment 필드로 매핑
    BooleanExpression : 하나의 조건식 / BooleanBuilder : 여러개의 조건식*/
    private BooleanExpression hasAttachment() {
        return JPAExpressions.selectOne().from(attachment).where(attachment.docId.eq(doc)).exists();
    }

    /*헬퍼 3 : 페이징 실행
     * content 쿼리와 count 쿼리를 받아서 pageㄹ 반환
     * pageableExecutionUtils가 content 수 < pagesize면 couunt 쿼리 생략 */
    private Page<DocumentListResponseDto> executedWithPagination(JPAQuery<DocumentListResponseDto> contentQuery, JPAQuery<Long> countQuery, Pageable pageable) {
        List<DocumentListResponseDto> content = contentQuery.offset(pageable.getOffset()).limit(pageable.getPageSize()).fetch();

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }


    /*결재 대기 상태
     * */
    @Override
    public Page<DocumentListResponseDto> findWaitingDocument(UUID companyId, Long empId, DocumentListSearchDto searchDto, Pageable pageable) {
        BooleanBuilder builder = applyCommonFilters(companyId, searchDto);

        // 서브쿼리용 별도 Q클래스 (같은 테이블을 서브쿼리에서 다시 참조하므로 별칭 필요)
        QApprovalLine subLine = new QApprovalLine("subLine");

        // EXISTS 서브쿼리: 결재라인에서 내가 현재 결재할 차례인 문서가 있는지 확인
        builder.and(
                JPAExpressions.selectOne()
                        .from(line)
                        .where(
                                line.docId.eq(doc),                                    // 같은 문서
                                line.empId.eq(empId),                                  // 내 결재라인
                                line.approvalRole.eq(ApprovalRole.APPROVER),           // 결재자 역할
                                line.approvalLineStatus.eq(ApprovalLineStatus.PENDING),// 대기 상태
                                line.lineStep.eq(                                      // 내 step = 최소 대기 step
                                        JPAExpressions.select(subLine.lineStep.min())
                                                .from(subLine)
                                                .where(
                                                        subLine.docId.eq(doc),
                                                        subLine.approvalRole.eq(ApprovalRole.APPROVER),
                                                        subLine.approvalLineStatus.eq(ApprovalLineStatus.PENDING)
                                                )
                                )
                        )
                        .exists()
        );

        // 문서 자체가 진행중 상태인 것만
        builder.and(doc.approvalStatus.eq(ApprovalStatus.PENDING));

        JPAQuery<DocumentListResponseDto> contentQuery = jpaQueryFactory
                .select(Projections.constructor(DocumentListResponseDto.class,
                        doc.docId, doc.docTitle, doc.docNum,
                        doc.approvalStatus.stringValue(), doc.isEmergency,
                        doc.formId.formName, doc.empName, doc.empDeptName,
                        doc.createdAt, hasAttachment()
                ))
                .from(doc)
                .where(builder)
                .orderBy(doc.isEmergency.desc(), doc.createdAt.desc());

        JPAQuery<Long> countQuery = jpaQueryFactory
                .select(doc.count())
                .from(doc)
                .where(builder);

        return executedWithPagination(contentQuery, countQuery, pageable);
    }


    /* 참조/열람  대기 */
    @Override
    public Page<DocumentListResponseDto> findCcViewDocument(UUID companyId, Long empId, DocumentListSearchDto searchDto, Pageable pageable) {
        BooleanBuilder builder = applyCommonFilters(companyId, searchDto);
        builder.and(
                JPAExpressions.selectOne()
                        .from(line)
                        .where(
                                line.docId.eq(doc),
                                line.empId.eq(empId),
                                line.approvalRole.in(ApprovalRole.REFERENCE, ApprovalRole.VIEWER),
                                line.isRead.eq(false)
                        )
                        .exists()
        );

        JPAQuery<DocumentListResponseDto> contentQuery = jpaQueryFactory
                .select(Projections.constructor(DocumentListResponseDto.class,
                        doc.docId, doc.docTitle, doc.docNum,
                        doc.approvalStatus.stringValue(), doc.isEmergency,
                        doc.formId.formName, doc.empName, doc.empDeptName,
                        doc.createdAt, hasAttachment()
                ))
                .from(doc)
                .where(builder)
                .orderBy(doc.isEmergency.desc(), doc.createdAt.desc());

        JPAQuery<Long> countQuery = jpaQueryFactory
                .select(doc.count()).from(doc).where(builder);

        return executedWithPagination(contentQuery, countQuery, pageable);
    }

    /* 결재 예정 문서함 */
    @Override
    public Page<DocumentListResponseDto> findUpcomingDocument(UUID companyId, Long empId, DocumentListSearchDto searchDto, Pageable pageable) {
        BooleanBuilder builder = applyCommonFilters(companyId, searchDto);

        QApprovalLine subLine = new QApprovalLine("subLine");

        builder.and(
                JPAExpressions.selectOne()
                        .from(line)
                        .where(
                                line.docId.eq(doc),
                                line.empId.eq(empId),
                                line.approvalRole.eq(ApprovalRole.APPROVER),
                                line.approvalLineStatus.eq(ApprovalLineStatus.PENDING),
                                line.lineStep.gt(                                      // 내 step > 최소 대기 step
                                        JPAExpressions.select(subLine.lineStep.min())
                                                .from(subLine)
                                                .where(
                                                        subLine.docId.eq(doc),
                                                        subLine.approvalRole.eq(ApprovalRole.APPROVER),
                                                        subLine.approvalLineStatus.eq(ApprovalLineStatus.PENDING)
                                                )
                                )
                        )
                        .exists()
        );

        builder.and(doc.approvalStatus.eq(ApprovalStatus.PENDING));

        JPAQuery<DocumentListResponseDto> contentQuery = jpaQueryFactory
                .select(Projections.constructor(DocumentListResponseDto.class,
                        doc.docId, doc.docTitle, doc.docNum,
                        doc.approvalStatus.stringValue(), doc.isEmergency,
                        doc.formId.formName, doc.empName, doc.empDeptName,
                        doc.createdAt, hasAttachment()
                ))
                .from(doc)
                .where(builder)
                .orderBy(doc.isEmergency.desc(), doc.createdAt.desc());

        JPAQuery<Long> countQuery = jpaQueryFactory
                .select(doc.count())
                .from(doc)
                .where(builder);

        return executedWithPagination(contentQuery, countQuery, pageable);
    }

    /*기안 문서함 - 사원이 기안한 문서
     * doc.empId = 나 , status != Draft */
    @Override
    public Page<DocumentListResponseDto> findDraftDocument(UUID companyId, Long empId, DocumentListSearchDto searchDto, Pageable pageable) {
        BooleanBuilder builder = applyCommonFilters(companyId, searchDto);
        builder.and(doc.empId.eq(empId));                              // 내가 기안한 문서
        builder.and(doc.approvalStatus.ne(ApprovalStatus.DRAFT));      // 임시저장 제외

        JPAQuery<DocumentListResponseDto> contentQuery = jpaQueryFactory
                .select(Projections.constructor(DocumentListResponseDto.class,
                        doc.docId,
                        doc.docTitle,
                        doc.docNum,
                        doc.approvalStatus.stringValue(),
                        doc.isEmergency,
                        doc.formId.formName,
                        doc.empName,
                        doc.empDeptName,
                        doc.createdAt,
                        hasAttachment()
                ))
                .from(doc)
                .where(builder)
                .orderBy(doc.isEmergency.desc(), doc.createdAt.desc());  // 긴급 우선, 최신순

        JPAQuery<Long> countQuery = jpaQueryFactory
                .select(doc.count())
                .from(doc)
                .where(builder);

        return executedWithPagination(contentQuery, countQuery, pageable);
    }

    /*임시 저장함
     * doc.empId = 나 , status = draft */
    @Override
    public Page<DocumentListResponseDto> findTempDocument(UUID companyId, Long empId, DocumentListSearchDto searchDto, Pageable pageable) {

        BooleanBuilder builder = applyCommonFilters(companyId, searchDto);
        builder.and(doc.empId.eq(empId));
        builder.and(doc.approvalStatus.eq(ApprovalStatus.DRAFT));      // 임시저장만

        JPAQuery<DocumentListResponseDto> contentQuery = jpaQueryFactory
                .select(Projections.constructor(DocumentListResponseDto.class,
                        doc.docId,
                        doc.docTitle,
                        doc.docNum,
                        doc.approvalStatus.stringValue(),
                        doc.isEmergency,
                        doc.formId.formName,
                        doc.empName,
                        doc.empDeptName,
                        doc.createdAt,
                        hasAttachment()
                ))
                .from(doc)
                .where(builder)
                .orderBy(doc.isEmergency.desc(), doc.createdAt.desc());

        JPAQuery<Long> countQuery = jpaQueryFactory
                .select(doc.count())
                .from(doc)
                .where(builder);

        return executedWithPagination(contentQuery, countQuery, pageable);
    }


    /* 결재 문서함 */
    @Override
    public Page<DocumentListResponseDto> findApprovedDocument(UUID companyId, Long empId, DocumentListSearchDto searchDto, Pageable pageable) {
        BooleanBuilder builder = applyCommonFilters(companyId, searchDto);
        builder.and(
                JPAExpressions.selectOne()
                        .from(line)
                        .where(
                                line.docId.eq(doc),
                                line.empId.eq(empId),
                                line.approvalRole.eq(ApprovalRole.APPROVER),
                                line.approvalLineStatus.in(ApprovalLineStatus.APPROVED, ApprovalLineStatus.REJECTED)
                        )
                        .exists()
        );

        JPAQuery<DocumentListResponseDto> contentQuery = jpaQueryFactory
                .select(Projections.constructor(DocumentListResponseDto.class,
                        doc.docId, doc.docTitle, doc.docNum,
                        doc.approvalStatus.stringValue(), doc.isEmergency,
                        doc.formId.formName, doc.empName, doc.empDeptName,
                        doc.createdAt, hasAttachment()
                ))
                .from(doc)
                .where(builder)
                .orderBy(doc.isEmergency.desc(), doc.createdAt.desc());

        JPAQuery<Long> countQuery = jpaQueryFactory
                .select(doc.count()).from(doc).where(builder);

        return executedWithPagination(contentQuery, countQuery, pageable);
    }

    /* 참조 열람 문서 -확인 완료  */
    @Override
    public Page<DocumentListResponseDto> findCcViewBoxDocument(UUID companyId, Long empId, DocumentListSearchDto searchDto, Pageable pageable) {
        BooleanBuilder builder = applyCommonFilters(companyId, searchDto);

        /*안읽은 문서가 위에 보이기 위해 어쩔 수 없이 조인사용*/
        JPAQuery<DocumentListResponseDto> contentQuery = jpaQueryFactory
                .select(Projections.constructor(DocumentListResponseDto.class,
                        doc.docId, doc.docTitle, doc.docNum,
                        doc.approvalStatus.stringValue(), doc.isEmergency,
                        doc.formId.formName, doc.empName, doc.empDeptName,
                        doc.createdAt, hasAttachment()
                ))
                .from(doc)
                .join(line).on(                          // EXISTS 대신 JOIN
                        line.docId.eq(doc),
                        line.empId.eq(empId),
                        line.approvalRole.in(ApprovalRole.REFERENCE, ApprovalRole.VIEWER)
                )
                .where(builder)
                .orderBy(
                        line.isRead.asc(),               // 미확인이 위로
                        doc.isEmergency.desc(),
                        doc.createdAt.desc()
                );

        JPAQuery<Long> countQuery = jpaQueryFactory
                .select(doc.count())
                .from(doc)
                .join(line).on(
                        line.docId.eq(doc),
                        line.empId.eq(empId),
                        line.approvalRole.in(ApprovalRole.REFERENCE, ApprovalRole.VIEWER)
                )
                .where(builder);

        return executedWithPagination(contentQuery, countQuery, pageable);
    }

    /* 수신 문서함 — 내가 결재/참조/열람으로 참여한 모든 문서 (임시저장 제외, 안읽음 우선 정렬) */
    @Override
    public Page<DocumentListResponseDto> findInboxDocument(UUID companyId, Long empId, DocumentListSearchDto searchDto, Pageable pageable) {
        BooleanBuilder builder = applyCommonFilters(companyId, searchDto);
        builder.and(doc.approvalStatus.ne(ApprovalStatus.DRAFT));

        JPAQuery<DocumentListResponseDto> contentQuery = jpaQueryFactory
                .select(Projections.constructor(DocumentListResponseDto.class,
                        doc.docId, doc.docTitle, doc.docNum,
                        doc.approvalStatus.stringValue(), doc.isEmergency,
                        doc.formId.formName, doc.empName, doc.empDeptName,
                        doc.createdAt, hasAttachment()
                ))
                .from(doc)
                .join(line).on(
                        line.docId.eq(doc),
                        line.empId.eq(empId)
                )
                .where(builder)
                .orderBy(
                        line.isRead.asc(),
                        doc.isEmergency.desc(),
                        doc.createdAt.desc()
                );

        JPAQuery<Long> countQuery = jpaQueryFactory
                .select(doc.count())
                .from(doc)
                .join(line).on(
                        line.docId.eq(doc),
                        line.empId.eq(empId)
                )
                .where(builder);

        return executedWithPagination(contentQuery, countQuery, pageable);
    }

    /* 개인 폴더 문서함 — PersonalFolderDocument 매핑 테이블 JOIN */
    @Override
    public Page<DocumentListResponseDto> findPersonalFolderDocument(UUID companyId, Long empId, Long folderId, DocumentListSearchDto searchDto, Pageable pageable) {
        BooleanBuilder builder = applyCommonFilters(companyId, searchDto);

        JPAQuery<DocumentListResponseDto> contentQuery = jpaQueryFactory
                .select(Projections.constructor(DocumentListResponseDto.class,
                        doc.docId, doc.docTitle, doc.docNum,
                        doc.approvalStatus.stringValue(), doc.isEmergency,
                        doc.formId.formName, doc.empName, doc.empDeptName,
                        doc.createdAt, hasAttachment()
                ))
                .from(doc)
                .join(folderDoc).on(
                        folderDoc.docId.eq(doc.docId),
                        folderDoc.companyId.eq(companyId),
                        folderDoc.empId.eq(empId),
                        folderDoc.personalFolderId.eq(folderId)
                )
                .where(builder)
                .orderBy(doc.isEmergency.desc(), doc.createdAt.desc());

        JPAQuery<Long> countQuery = jpaQueryFactory
                .select(doc.count())
                .from(doc)
                .join(folderDoc).on(
                        folderDoc.docId.eq(doc.docId),
                        folderDoc.companyId.eq(companyId),
                        folderDoc.empId.eq(empId),
                        folderDoc.personalFolderId.eq(folderId)
                )
                .where(builder);

        return executedWithPagination(contentQuery, countQuery, pageable);
    }

    /* 부서 완료 문서함 -> 부서원이 기안한 승인 완료 문서 */
    @Override
    public Page<DocumentListResponseDto> findDeptCompletedDocument(UUID companyId, Long deptId, DocumentListSearchDto searchDto, Pageable pageable) {
        BooleanBuilder builder = applyCommonFilters(companyId, searchDto);
        builder.and(doc.empDeptId.eq(deptId));
        builder.and(doc.approvalStatus.eq(ApprovalStatus.APPROVED));

        JPAQuery<DocumentListResponseDto> contentQuery = jpaQueryFactory
                .select(Projections.constructor(DocumentListResponseDto.class,
                        doc.docId, doc.docTitle, doc.docNum,
                        doc.approvalStatus.stringValue(), doc.isEmergency,
                        doc.formId.formName, doc.empName, doc.empDeptName,
                        doc.createdAt, hasAttachment()
                ))
                .from(doc)
                .where(builder)
                .orderBy(doc.isEmergency.desc(), doc.createdAt.desc());

        JPAQuery<Long> countQuery = jpaQueryFactory
                .select(doc.count()).from(doc).where(builder);

        return executedWithPagination(contentQuery, countQuery, pageable);
    }

    /* 부서 결재 수신함 */
    @Override
    public Page<DocumentListResponseDto> findDeptReceiveDocument(UUID companyId, Long deptId, DocumentListSearchDto searchDto, Pageable pageable) {
        BooleanBuilder builder = applyCommonFilters(companyId, searchDto);
        builder.and(doc.approvalStatus.eq(ApprovalStatus.PENDING));
        builder.and(
                JPAExpressions.selectOne()
                        .from(line)
                        .where(
                                line.docId.eq(doc),
                                line.empDeptId.eq(deptId),
                                line.approvalRole.eq(ApprovalRole.APPROVER),
                                line.approvalLineStatus.eq(ApprovalLineStatus.PENDING)
                        )
                        .exists()
        );

        JPAQuery<DocumentListResponseDto> contentQuery = jpaQueryFactory
                .select(Projections.constructor(DocumentListResponseDto.class,
                        doc.docId, doc.docTitle, doc.docNum,
                        doc.approvalStatus.stringValue(), doc.isEmergency,
                        doc.formId.formName, doc.empName, doc.empDeptName,
                        doc.createdAt, hasAttachment()
                ))
                .from(doc)
                .where(builder)
                .orderBy(doc.isEmergency.desc(), doc.createdAt.desc());

        JPAQuery<Long> countQuery = jpaQueryFactory
                .select(doc.count()).from(doc).where(builder);

        return executedWithPagination(contentQuery, countQuery, pageable);
    }

    /**
     * 전체 문서함 건수 조회 — DB 왕복 2회 (결재선 기반 1회 + 기안자 기반 1회)
     * 결재선 기반: waiting, ccView, upcoming, approved, ccViewBox, inbox
     * 기안자 기반: draft, temp
     */
    @Override
    public DocumentCountResponse countAllBoxes(UUID companyId, Long empId, Long deptId) {
        QApprovalLine subLine = new QApprovalLine("subLine");

        /* 결재 대기: 나=APPROVER, PENDING, 내 step=최소 PENDING step, 문서 PENDING */
        NumberExpression<Integer> waitingCase = new CaseBuilder()
                .when(line.approvalRole.eq(ApprovalRole.APPROVER)
                        .and(line.approvalLineStatus.eq(ApprovalLineStatus.PENDING))
                        .and(doc.approvalStatus.eq(ApprovalStatus.PENDING))
                        .and(line.lineStep.eq(
                                JPAExpressions.select(subLine.lineStep.min())
                                        .from(subLine)
                                        .where(subLine.docId.eq(doc),
                                                subLine.approvalRole.eq(ApprovalRole.APPROVER),
                                                subLine.approvalLineStatus.eq(ApprovalLineStatus.PENDING))
                        )))
                .then(1).otherwise(0);

        /* 참조/열람 대기: 나=REFERENCE/VIEWER, isRead=false */
        NumberExpression<Integer> ccViewCase = new CaseBuilder()
                .when(line.approvalRole.in(ApprovalRole.REFERENCE, ApprovalRole.VIEWER)
                        .and(line.isRead.eq(false)))
                .then(1).otherwise(0);

        /* 결재 예정: 나=APPROVER, PENDING, 내 step > 최소 PENDING step, 문서 PENDING */
        NumberExpression<Integer> upcomingCase = new CaseBuilder()
                .when(line.approvalRole.eq(ApprovalRole.APPROVER)
                        .and(line.approvalLineStatus.eq(ApprovalLineStatus.PENDING))
                        .and(doc.approvalStatus.eq(ApprovalStatus.PENDING))
                        .and(line.lineStep.gt(
                                JPAExpressions.select(subLine.lineStep.min())
                                        .from(subLine)
                                        .where(subLine.docId.eq(doc),
                                                subLine.approvalRole.eq(ApprovalRole.APPROVER),
                                                subLine.approvalLineStatus.eq(ApprovalLineStatus.PENDING))
                        )))
                .then(1).otherwise(0);

        /* 결재 완료: 나=APPROVER, lineStatus=APPROVED/REJECTED */
        NumberExpression<Integer> approvedCase = new CaseBuilder()
                .when(line.approvalRole.eq(ApprovalRole.APPROVER)
                        .and(line.approvalLineStatus.in(ApprovalLineStatus.APPROVED, ApprovalLineStatus.REJECTED)))
                .then(1).otherwise(0);

        /* 참조/열람 문서함: 나=REFERENCE/VIEWER (읽음 무관) */
        NumberExpression<Integer> ccViewBoxCase = new CaseBuilder()
                .when(line.approvalRole.in(ApprovalRole.REFERENCE, ApprovalRole.VIEWER))
                .then(1).otherwise(0);

        /* 수신 문서함: 모든 역할, 문서 != DRAFT */
        NumberExpression<Integer> inboxCase = new CaseBuilder()
                .when(doc.approvalStatus.ne(ApprovalStatus.DRAFT))
                .then(1).otherwise(0);

        Tuple result = jpaQueryFactory
                .select(
                        waitingCase.sum(),
                        ccViewCase.sum(),
                        upcomingCase.sum(),
                        approvedCase.sum(),
                        ccViewBoxCase.sum(),
                        inboxCase.sum()
                )
                .from(line)
                .join(line.docId, doc)
                .where(line.empId.eq(empId), doc.companyId.eq(companyId), notExpired())
                .fetchOne();

        /* 기안자 기반: draft(!=DRAFT), temp(=DRAFT) — 별도 1회 */
        Tuple drafterResult = jpaQueryFactory
                .select(
                        new CaseBuilder()
                                .when(doc.approvalStatus.ne(ApprovalStatus.DRAFT))
                                .then(1).otherwise(0).sum(),
                        new CaseBuilder()
                                .when(doc.approvalStatus.eq(ApprovalStatus.DRAFT))
                                .then(1).otherwise(0).sum()
                )
                .from(doc)
                .where(doc.companyId.eq(companyId), doc.empId.eq(empId), notExpired())
                .fetchOne();

        /* 부서 문서함: 완료/수신/발신 — 별도 1회 */
        Tuple deptResult = jpaQueryFactory
                .select(
                        new CaseBuilder()
                                .when(doc.empDeptId.eq(deptId)
                                        .and(doc.approvalStatus.eq(ApprovalStatus.APPROVED)))
                                .then(1).otherwise(0).sum(),
                        new CaseBuilder()
                                .when(doc.approvalStatus.eq(ApprovalStatus.PENDING)
                                        .and(JPAExpressions.selectOne()
                                                .from(line)
                                                .where(line.docId.eq(doc),
                                                        line.empDeptId.eq(deptId),
                                                        line.approvalRole.eq(ApprovalRole.APPROVER),
                                                        line.approvalLineStatus.eq(ApprovalLineStatus.PENDING))
                                                .exists()))
                                .then(1).otherwise(0).sum(),
                        new CaseBuilder()
                                .when(doc.empDeptId.eq(deptId)
                                        .and(doc.approvalStatus.ne(ApprovalStatus.DRAFT)))
                                .then(1).otherwise(0).sum()
                )
                .from(doc)
                .where(doc.companyId.eq(companyId), notExpired())
                .fetchOne();

        /* 부서 폴더별 문서 개수 */
        Map<Long, Long> deptFolderCounts = jpaQueryFactory
                .select(doc.deptFolderId, doc.count())
                .from(doc)
                .where(doc.companyId.eq(companyId),
                        doc.deptFolderId.isNotNull(),
                        doc.empDeptId.eq(deptId),
                        notExpired())
                .groupBy(doc.deptFolderId)
                .fetch()
                .stream()
                .collect(Collectors.toMap(
                        t -> t.get(doc.deptFolderId),
                        t -> t.get(doc.count())
                ));

        /* 개인 폴더별 문서 개수 - 만료 필터 적용 위해 doc JOIN */
        Map<Long, Long> personalFolderCounts = jpaQueryFactory
                .select(folderDoc.personalFolderId, folderDoc.count())
                .from(folderDoc)
                .join(doc).on(doc.docId.eq(folderDoc.docId))
                .where(folderDoc.companyId.eq(companyId),
                        folderDoc.empId.eq(empId),
                        notExpired())
                .groupBy(folderDoc.personalFolderId)
                .fetch()
                .stream()
                .collect(Collectors.toMap(
                        t -> t.get(folderDoc.personalFolderId),
                        t -> t.get(folderDoc.count())
                ));

        return DocumentCountResponse.builder()
                .waiting(result != null && result.get(0, Integer.class) != null ? result.get(0, Integer.class) : 0)
                .ccView(result != null && result.get(1, Integer.class) != null ? result.get(1, Integer.class) : 0)
                .upcoming(result != null && result.get(2, Integer.class) != null ? result.get(2, Integer.class) : 0)
                .approved(result != null && result.get(3, Integer.class) != null ? result.get(3, Integer.class) : 0)
                .ccViewBox(result != null && result.get(4, Integer.class) != null ? result.get(4, Integer.class) : 0)
                .inbox(result != null && result.get(5, Integer.class) != null ? result.get(5, Integer.class) : 0)
                .draft(drafterResult != null && drafterResult.get(0, Integer.class) != null ? drafterResult.get(0, Integer.class) : 0)
                .temp(drafterResult != null && drafterResult.get(1, Integer.class) != null ? drafterResult.get(1, Integer.class) : 0)
                .deptCompleted(deptResult != null && deptResult.get(0, Integer.class) != null ? deptResult.get(0, Integer.class) : 0)
                .deptReceived(deptResult != null && deptResult.get(1, Integer.class) != null ? deptResult.get(1, Integer.class) : 0)
                .deptSent(deptResult != null && deptResult.get(2, Integer.class) != null ? deptResult.get(2, Integer.class) : 0)
                .deptFolderCounts(deptFolderCounts)
                .personalFolderCounts(personalFolderCounts)
                .build();
    }

    /*부서 결재 발신함*/
    @Override
    public Page<DocumentListResponseDto> findDeptSentDocument(UUID companyId, Long deptId, DocumentListSearchDto searchDto, Pageable pageable) {
        BooleanBuilder builder = applyCommonFilters(companyId, searchDto);
        builder.and(doc.empDeptId.eq(deptId));
        builder.and(doc.approvalStatus.ne(ApprovalStatus.DRAFT));

        JPAQuery<DocumentListResponseDto> contentQuery = jpaQueryFactory
                .select(Projections.constructor(DocumentListResponseDto.class,
                        doc.docId, doc.docTitle, doc.docNum,
                        doc.approvalStatus.stringValue(), doc.isEmergency,
                        doc.formId.formName, doc.empName, doc.empDeptName,
                        doc.createdAt, hasAttachment()
                ))
                .from(doc)
                .where(builder)
                .orderBy(doc.isEmergency.desc(), doc.createdAt.desc());

        JPAQuery<Long> countQuery = jpaQueryFactory
                .select(doc.count()).from(doc).where(builder);

        return executedWithPagination(contentQuery, countQuery, pageable);
    }
}
