package com.peoplecore.holiday.service;

import com.peoplecore.employee.repository.EmployeeRepository;
import com.peoplecore.entity.HolidayType;
import com.peoplecore.entity.Holidays;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import com.peoplecore.holiday.dtos.HolidayReqDto;
import com.peoplecore.holiday.dtos.HolidayResDto;
import com.peoplecore.holiday.repository.HolidayAdminRepository;
import com.peoplecore.vacation.service.BusinessDayCalculator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Service
@Slf4j
@Transactional(readOnly = true)
public class HolidayAdminService {

    private final HolidayAdminRepository holidayAdminRepository;
    private final BusinessDayCalculator businessDayCalculator;

    @Autowired
    public HolidayAdminService(HolidayAdminRepository holidayAdminRepository, BusinessDayCalculator businessDayCalculator) {
        this.holidayAdminRepository = holidayAdminRepository;
        this.businessDayCalculator = businessDayCalculator;
    }


    // 연도 + 타입 필터 조회. 반복 휴일은 year 기준 occurrenceDate 매핑
    // 비반복 휴일은 year 와 일치하는 것만 노출
    public List<HolidayResDto> list(UUID companyId, int year, String typeFilter) {
        List<Holidays> all = holidayAdminRepository.findAllForCompany(companyId);

        HolidayType filter = parseTypeFilter(typeFilter);

        List<HolidayResDto> result = new ArrayList<>();
        for (Holidays h : all) {
            if (filter != null && h.getHolidayType() != filter) continue;

            LocalDate occurrence = resolveOccurrence(h, year);
            if (occurrence == null) continue; // 해당 year 에 발생하지 않음

            result.add(HolidayResDto.fromEntity(h, occurrence));
        }

        result.sort(Comparator.comparing(HolidayResDto::getOccurrenceDate));
        return result;
    }


    @Transactional
    public HolidayResDto create(UUID companyId, Long empId, HolidayReqDto req) {
        validateDuplicate(companyId, req.getDate(), req.getIsRepeating(), null);

        Holidays saved = holidayAdminRepository.save(Holidays.builder()
                .date(req.getDate())
                .holidayName(req.getHolidayName())
                .holidayType(HolidayType.COMPANY)
                .isRepeating(req.getIsRepeating())
                .companyId(companyId)
                .empId(empId)
                .build());

        registerEvictAfterCommit(companyId, req.getIsRepeating(), req.getDate(), null, null);

        return HolidayResDto.fromEntity(saved, resolveOccurrence(saved, req.getDate().getYear()));
    }


    @Transactional
    public HolidayResDto update(UUID companyId, Long empId, Long holidayId, HolidayReqDto req) {
        Holidays target = holidayAdminRepository.findById(holidayId)
                .orElseThrow(() -> new CustomException(ErrorCode.HOLIDAY_NOT_FOUND));

        if (target.getHolidayType() != HolidayType.COMPANY) {
            throw new CustomException(ErrorCode.HOLIDAY_NOT_COMPANY);
        }
        if (!companyId.equals(target.getCompanyId())) {
            throw new CustomException(ErrorCode.HOLIDAY_ACCESS_DENIED);
        }

        validateDuplicate(companyId, req.getDate(), req.getIsRepeating(), holidayId);

        LocalDate beforeDate = target.getDate();
        Boolean beforeRepeating = target.getIsRepeating();

        /* 엔티티에 도메인 메서드를 추가하기 어렵다면 reflection 회피용 Builder 새로 save 도 가능.
         * 여기서는 Holidays 엔티티에 update 메서드를 추가했다고 가정. (10.6 참고) */
        target.update(req.getDate(), req.getHolidayName(), req.getIsRepeating(), empId);

        registerEvictAfterCommit(companyId, beforeRepeating, beforeDate, req.getIsRepeating(), req.getDate());

        return HolidayResDto.fromEntity(target, resolveOccurrence(target, req.getDate().getYear()));
    }


    @Transactional
    public void delete(UUID companyId, Long holidayId) {
        Holidays target = holidayAdminRepository.findById(holidayId)
                .orElseThrow(() -> new CustomException(ErrorCode.HOLIDAY_NOT_FOUND));

        if (target.getHolidayType() != HolidayType.COMPANY) {
            throw new CustomException(ErrorCode.HOLIDAY_NOT_COMPANY);
        }
        if (!companyId.equals(target.getCompanyId())) {
            throw new CustomException(ErrorCode.HOLIDAY_ACCESS_DENIED);
        }

        Boolean wasRepeating = target.getIsRepeating();
        LocalDate wasDate = target.getDate();

        holidayAdminRepository.delete(target);

        registerEvictAfterCommit(companyId, wasRepeating, wasDate, null, null);
    }


    // 비반복: date.year == year 인 것만
    // 반복: 매년 노출 (해당 월/일을 year에 매핑)
    private LocalDate resolveOccurrence(Holidays h, int year) {
        if (Boolean.TRUE.equals(h.getIsRepeating())) {
            try {
                return LocalDate.of(year, h.getDate().getMonth(), h.getDate().getDayOfMonth());
            } catch (Exception e) {
                /* 2/29 윤년 케이스 - 평년에는 2/28 로 매핑 */
                return LocalDate.of(year, h.getDate().getMonth(), 28);
            }
        }
        return h.getDate().getYear() == year ? h.getDate() : null;
    }

    private void validateDuplicate(UUID companyId, LocalDate date, Boolean isRepeating, Long excludeId) {
        if (Boolean.TRUE.equals(isRepeating)) {
            holidayAdminRepository.findRepeatingCompanyByMonthDay(
                            companyId, date.getMonthValue(), date.getDayOfMonth())
                    .filter(h -> excludeId == null || !excludeId.equals(h.getHolidayId()))
                    .ifPresent(h -> { throw new CustomException(ErrorCode.HOLIDAY_DUPLICATED); });
        } else {
            holidayAdminRepository.findFirstByCompanyIdAndHolidayTypeAndDateAndIsRepeating(
                            companyId, HolidayType.COMPANY, date, false)
                    .filter(h -> excludeId == null || !excludeId.equals(h.getHolidayId()))
                    .ifPresent(h -> { throw new CustomException(ErrorCode.HOLIDAY_DUPLICATED); });
        }
    }

    private HolidayType parseTypeFilter(String typeFilter) {
        if (typeFilter == null || typeFilter.isBlank() || "ALL".equalsIgnoreCase(typeFilter)) return null;
        try {
            return HolidayType.valueOf(typeFilter.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // === 캐시 evict (트랜잭션 afterCommit 에 등록) ===
    // before/after 구분: update 시 before/after 모두 evict.
    // COMPANY 반복 토글 또는 반복 여부 변경은 evictCompany.
    private void registerEvictAfterCommit(UUID companyId,
                                          Boolean beforeRepeating, LocalDate beforeDate,
                                          Boolean afterRepeating, LocalDate afterDate) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                /* 반복 휴일 변경이 한 번이라도 끼면(before/after에 한번이라도 있다면) 회사 전체 캐시 무효화 */
                boolean repeatingTouched =
                        Boolean.TRUE.equals(beforeRepeating) || Boolean.TRUE.equals(afterRepeating);
                if (repeatingTouched) {
                    businessDayCalculator.evictCompany(companyId);
                    log.debug("[HolidayAdminService] evictCompany - companyId={}", companyId);
                    return;
                }
                /* 비반복 휴일 - 영향받은 월만 무효화 */
                if (beforeDate != null) {
                    businessDayCalculator.evictMonth(companyId, YearMonth.from(beforeDate));
                }
                if (afterDate != null && (beforeDate == null
                        || !YearMonth.from(beforeDate).equals(YearMonth.from(afterDate)))) {
                    businessDayCalculator.evictMonth(companyId, YearMonth.from(afterDate));
                }
                log.debug("[HolidayAdminService] evictMonth - companyId={}, before={}, after={}",
                        companyId, beforeDate, afterDate);
            }
        });
    }
}