package com.peoplecore.attendance.service;

import com.peoplecore.attendance.entity.AttendanceCardType;
import com.peoplecore.attendance.entity.CommuteRecord;
import com.peoplecore.attendance.entity.WorkGroup;
import com.peoplecore.attendance.entity.WorkStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/*
 * 일별 단건 판정 (사원 근무이력 화면용).
 * 주간 컨텍스트가 없으므로 MAX_HOUR_EXCEED / UNDER_MIN_HOUR / VACATION_ATTEND 미적용.
 *
 * WorkStatus 분기 — EnumMap WS_STRATEGY (상태 패턴)
 *  · true  반환 : 조기 반환 (이후 조건 카드 불필요 — ex. ABSENT)
 *  · false 반환 : 조건 카드 이어서 처리
 *
 * 조건 카드 (런타임 값 기반):
 *  - MISSING_COMMUTE : 체크인 있고 체크아웃 없음
 *  - UNAPPROVED_OT   : 체크아웃 > groupEndTime + 승인 OT 없음
 *  - NORMAL          : 체크인 있고 위 이상 상태 없으면
 */
@Component
public class HistoricalDayJudge {

    /* WorkStatus → 카드 추가 전략. true = 조기 반환 */
    @FunctionalInterface
    private interface WorkStatusStrategy {
        boolean resolve(List<AttendanceCardType> out);
    }

    /* 등록 없는 WorkStatus 기본 동작 */
    private static final WorkStatusStrategy NO_OP = out -> false;

    private static final EnumMap<WorkStatus, WorkStatusStrategy> WS_STRATEGY = new EnumMap<>(WorkStatus.class);
    static {
        WS_STRATEGY.put(WorkStatus.ABSENT,
                out -> { out.add(AttendanceCardType.ABSENT); return true; });
        WS_STRATEGY.put(WorkStatus.LATE,
                out -> { out.add(AttendanceCardType.LATE); return false; });
        WS_STRATEGY.put(WorkStatus.EARLY_LEAVE,
                out -> { out.add(AttendanceCardType.EARLY_LEAVE); return false; });
        WS_STRATEGY.put(WorkStatus.LATE_AND_EARLY, out -> {
            out.add(AttendanceCardType.LATE);
            out.add(AttendanceCardType.EARLY_LEAVE);
            return false;
        });
        /* NORMAL / AUTO_CLOSED / HOLIDAY_WORK → NO_OP */
    }

    /*
     * 일별 단건 판정.
     * @param c CommuteRecord (당일 출퇴근 레코드)
     * @param wg 사원 근무그룹 (UNAPPROVED_OT 판정용, null 허용)
     * @param approvedOt 해당일 승인 OT 분
     */
    public List<AttendanceCardType> judge(CommuteRecord c, WorkGroup wg, long approvedOt) {
        List<AttendanceCardType> out = new ArrayList<>();

        /* WorkStatus 전략 — true 면 조기 반환 */
        if (WS_STRATEGY.getOrDefault(c.getWorkStatus(), NO_OP).resolve(out)) return out;

        boolean hasCheckIn  = (c.getComRecCheckIn()  != null);
        boolean hasCheckOut = (c.getComRecCheckOut() != null);

        /* 체크인 있고 체크아웃 없으면 누락 */
        if (hasCheckIn && !hasCheckOut) out.add(AttendanceCardType.MISSING_COMMUTE);
        /* 미승인 초과근무 */
        if (hasCheckOut && wg != null && wg.getGroupEndTime() != null && approvedOt == 0
                && c.getComRecCheckOut().toLocalTime().isAfter(wg.getGroupEndTime()))
            out.add(AttendanceCardType.UNAPPROVED_OT);
        /* 정상 */
        if (hasCheckIn && out.isEmpty()) out.add(AttendanceCardType.NORMAL);
        return out;
    }
}
