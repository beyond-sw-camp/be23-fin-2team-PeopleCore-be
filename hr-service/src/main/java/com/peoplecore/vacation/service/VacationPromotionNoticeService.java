package com.peoplecore.vacation.service;

import com.peoplecore.vacation.dto.VacationPromotionNoticeResponse;
import com.peoplecore.vacation.repository.VacationPromotionNoticeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/* 연차 촉진 통지 이력 조회 서비스 - 관리자/사원 화면용 */
/* 발송 자체는 PromotionNoticeScheduler / PromotionNoticeService 가 담당 (이 서비스는 조회 전용) */
@Service
@Slf4j
@Transactional(readOnly = true)
public class VacationPromotionNoticeService {

    private final VacationPromotionNoticeRepository vacationPromotionNoticeRepository;

    @Autowired
    public VacationPromotionNoticeService(VacationPromotionNoticeRepository vacationPromotionNoticeRepository) {
        this.vacationPromotionNoticeRepository = vacationPromotionNoticeRepository;
    }

    /* 관리자 회사 통지 이력 (페이지). year null 이면 전체 연도 통합 */
    public Page<VacationPromotionNoticeResponse> listForCompany(UUID companyId, Integer year, Pageable pageable) {
        if (year != null) {
            return vacationPromotionNoticeRepository
                    .findAllByCompanyIdAndNoticeYearOrderByNoticeSentAtDesc(companyId, year, pageable)
                    .map(VacationPromotionNoticeResponse::from);
        }
        return vacationPromotionNoticeRepository
                .findAllByCompanyIdOrderByNoticeSentAtDesc(companyId, pageable)
                .map(VacationPromotionNoticeResponse::from);
    }

    /* 사원 본인 통지 이력 - 1사원/1연도 최대 2건 (1차/2차) 이라 페이지 불필요 */
    /* year 생략 시 올해 */
    public List<VacationPromotionNoticeResponse> listMine(UUID companyId, Long empId, Integer year) {
        Integer targetYear = year != null ? year : LocalDate.now().getYear();
        return vacationPromotionNoticeRepository
                .findAllByCompanyIdAndEmpIdAndNoticeYearOrderByNoticeSentAtAsc(companyId, empId, targetYear)
                .stream()
                .map(VacationPromotionNoticeResponse::from)
                .toList();
    }
}