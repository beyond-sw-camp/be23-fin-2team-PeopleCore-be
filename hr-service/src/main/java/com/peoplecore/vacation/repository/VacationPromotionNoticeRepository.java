package com.peoplecore.vacation.repository;

import com.peoplecore.vacation.entity.VacationPromotionNotice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/* 연차 촉진 통지 이력 레포 - 근로기준법 제61조 대응 */
@Repository
public interface VacationPromotionNoticeRepository extends JpaRepository<VacationPromotionNotice, Long> {

    /*
     * 사원 + 연도 + 단계 통지 단건 조회
     * 용도: 통지 화면 상세, 응답 갱신 시
     * 인덱스: uk_vacation_notice_company_emp_year_stage (커버)
     */
    Optional<VacationPromotionNotice> findByCompanyIdAndEmpIdAndNoticeYearAndNoticeStage(
            UUID companyId, Long empId, Integer noticeYear, String noticeStage);

    /*
     * 단계별 발송 여부 체크 (스케줄러 멱등성)
     * 용도: 촉진 통지 잡 - 같은 사원/연도/단계 중복 발송 방지
     * 반환: true 면 이미 발송됨 → 잡이 스킵
     */
    boolean existsByCompanyIdAndEmpIdAndNoticeYearAndNoticeStage(
            UUID companyId, Long empId, Integer noticeYear, String noticeStage);

    /*
     * 사원 연도 모든 통지 정렬 조회 (1차/2차)
     * 용도: 사원 화면 "내 연차 촉진 이력"
     * 정렬: noticeSentAt 오름차순 (1차 → 2차 순서)
     */
    List<VacationPromotionNotice> findAllByCompanyIdAndEmpIdAndNoticeYearOrderByNoticeSentAtAsc(
            UUID companyId, Long empId, Integer noticeYear);

    /*
     * 회사 통지 이력 페이지 조회
     * 용도: 관리자 화면 "촉진 이력" 탭 (화면 캡처의 두 번째 탭)
     * 정렬: 최신순
     * 인덱스: idx_vacation_notice_company_sent
     */
    Page<VacationPromotionNotice> findAllByCompanyIdOrderByNoticeSentAtDesc(
            UUID companyId, Pageable pageable);

    /*
     * 사원 + 연도 1차/2차 통지 모두 발송됐는지 체크 (LeaveAllowance 면제 조건)
     * 용도: LeaveAllowanceService - 1차+2차 둘 다 통지 이력 있을 때만 수당 면제 인정
     * 반환: 1차/2차 통지 row 개수 (2 면 둘 다 발송됨)
     */
    @Query("""
            SELECT COUNT(n) FROM VacationPromotionNotice n
             WHERE n.companyId = :companyId
               AND n.empId = :empId
               AND n.noticeYear = :year
               AND n.noticeStage IN ('FIRST', 'SECOND')
            """)
    long countNoticeStages(@Param("companyId") UUID companyId,
                          @Param("empId") Long empId,
                          @Param("year") Integer year);

    /*
     * 회사 + 연도 통지 이력 페이지 (관리자 "촉진 이력" 탭 연도 필터용)
     * 정렬: 최신순 (noticeSentAt desc)
     */
    Page<VacationPromotionNotice> findAllByCompanyIdAndNoticeYearOrderByNoticeSentAtDesc(
            UUID companyId, Integer noticeYear, Pageable pageable);
}