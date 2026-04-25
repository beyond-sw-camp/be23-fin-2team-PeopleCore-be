package com.peoplecore.attendance.service;

import com.peoplecore.attendance.entity.HolidayReason;
import com.peoplecore.attendance.entity.WorkStatus;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.EnumMap;

/*
 * 체크인/체크아웃 시점의 WorkStatus 결정.
 *
 * 체크인 (resolveInitial):
 *   - 휴일이면 HOLIDAY_WORK
 *   - 정시 이후 출근이면 LATE
 *   - 그 외 NORMAL
 *
 * 체크아웃 (resolveFinal):
 *   - 현재 상태 + 조퇴 여부 → 최종 WorkStatus (EnumMap 상태 패턴)
 *   - NORMAL/LATE/HOLIDAY_WORK 외 상태는 checkOut 시점 도달 불가 — IllegalStateException
 */
@Component
public class WorkStatusResolver {

    /* 체크아웃 시 (조퇴 여부) → 최종 WorkStatus 전이 함수 */
    @FunctionalInterface
    private interface CheckOutTransition {
        WorkStatus apply(boolean isEarly);
    }

    private static final EnumMap<WorkStatus, CheckOutTransition> CHECKOUT_TRANSITIONS =
            new EnumMap<>(WorkStatus.class);
    static {
        CHECKOUT_TRANSITIONS.put(WorkStatus.NORMAL,
                early -> early ? WorkStatus.EARLY_LEAVE : WorkStatus.NORMAL);
        CHECKOUT_TRANSITIONS.put(WorkStatus.LATE,
                early -> early ? WorkStatus.LATE_AND_EARLY : WorkStatus.LATE);
        CHECKOUT_TRANSITIONS.put(WorkStatus.HOLIDAY_WORK,
                early -> WorkStatus.HOLIDAY_WORK);
    }

    /* 체크인 시 초기 WorkStatus 결정 */
    public WorkStatus resolveInitial(LocalTime now, LocalTime groupStart, HolidayReason reason) {
        if (reason != null) return WorkStatus.HOLIDAY_WORK;
        return now.isAfter(groupStart) ? WorkStatus.LATE : WorkStatus.NORMAL;
    }

    /* 체크아웃 시 최종 WorkStatus 결정 — 상태 전이표 dispatch */
    public WorkStatus resolveFinal(WorkStatus current, LocalTime now, LocalTime groupEnd) {
        boolean isEarly = now.isBefore(groupEnd);
        CheckOutTransition transition = CHECKOUT_TRANSITIONS.get(current);
        if (transition == null) {
            throw new IllegalStateException("checkOut 시 처리 불가능한 WorkStatus: " + current);
        }
        return transition.apply(isEarly);
    }
}
