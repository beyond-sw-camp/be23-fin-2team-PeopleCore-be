package com.peoplecore.attendance.service;

import com.peoplecore.attendance.dto.AttendanceAdminRow;
import com.peoplecore.attendance.entity.AttendanceCardType;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;

/*
 * 카드 타입별 detail 텍스트 포매터 — EnumMap 상태 패턴.
 *
 * 카드 추가 시:
 *   1) AttendanceCardType enum 에 값 추가
 *   2) init() 에 formatters.put(NEW_CARD, this::formatNewCard) 한 줄 추가
 *   3) 본 클래스에 private formatNewCard 메서드 작성
 */
@Component
public class CardDetailFormatter {

    /* 시각 표시용 HH:mm */
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    /* 카드별 포매팅 함수 — (row, weeklyMaxMinutes) → detail 문자열 */
    @FunctionalInterface
    private interface DetailFn {
        String apply(AttendanceAdminRow row, int weeklyMaxMinutes);
    }

    private final EnumMap<AttendanceCardType, DetailFn> formatters = new EnumMap<>(AttendanceCardType.class);

    @PostConstruct
    private void init() {
        formatters.put(AttendanceCardType.NORMAL,          (r, max) -> "정시 출근 · 정시 퇴근");
        formatters.put(AttendanceCardType.ABSENT,          (r, max) -> "결근");
        formatters.put(AttendanceCardType.LATE,            this::formatLate);
        formatters.put(AttendanceCardType.EARLY_LEAVE,     this::formatEarlyLeave);
        formatters.put(AttendanceCardType.VACATION_ATTEND, this::formatVacationAttend);
        formatters.put(AttendanceCardType.MISSING_COMMUTE, this::formatMissingCommute);
        formatters.put(AttendanceCardType.UNDER_MIN_HOUR,  this::formatUnderMinHour);
        formatters.put(AttendanceCardType.UNAPPROVED_OT,   this::formatUnapprovedOt);
        formatters.put(AttendanceCardType.MAX_HOUR_EXCEED, this::formatMaxHourExceed);
    }

    /* 등록된 포매터 실행. 미등록 카드는 빈 문자열 */
    public String format(AttendanceCardType cardType, AttendanceAdminRow row, int weeklyMaxMinutes) {
        DetailFn fn = formatters.get(cardType);
        return (fn != null) ? fn.apply(row, weeklyMaxMinutes) : "";
    }

    /* 분 → "Xh Ym" — 다른 서비스 재사용 가능하게 static 노출 */
    public static String formatHm(long minutes) {
        long h = minutes / 60;
        long m = minutes % 60;
        return h + "h " + m + "m";
    }

    /* 지각: "HH:mm 출근 (N분 지각)" */
    private String formatLate(AttendanceAdminRow r, int max) {
        long lateMin = (r.getCheckInAt() != null && r.getGroupStartTime() != null)
                ? Duration.between(r.getGroupStartTime(), r.getCheckInAt().toLocalTime()).toMinutes()
                : 0L;
        return String.format("%s 출근 (%d분 지각)",
                r.getCheckInAt() != null ? r.getCheckInAt().toLocalTime().format(HHMM) : "--:--",
                Math.max(0, lateMin));
    }

    /* 조퇴: "HH:mm 퇴근 (N분 조퇴)" */
    private String formatEarlyLeave(AttendanceAdminRow r, int max) {
        long earlyMin = (r.getCheckOutAt() != null && r.getGroupEndTime() != null)
                ? Duration.between(r.getCheckOutAt().toLocalTime(), r.getGroupEndTime()).toMinutes()
                : 0L;
        return String.format("%s 퇴근 (%d분 조퇴)",
                r.getCheckOutAt() != null ? r.getCheckOutAt().toLocalTime().format(HHMM) : "--:--",
                Math.max(0, earlyMin));
    }

    /* 휴가 중 출근: "{유형명} 중 출근" */
    private String formatVacationAttend(AttendanceAdminRow r, int max) {
        String type = (r.getVacationTypeName() != null) ? r.getVacationTypeName() : "휴가";
        return type + " 중 출근";
    }

    /* 출퇴근 누락: 체크인 없음 → "출근 누락", 체크아웃 없음 → "퇴근 누락" */
    private String formatMissingCommute(AttendanceAdminRow r, int max) {
        return (r.getCheckInAt() == null) ? "출근 누락" : "퇴근 누락";
    }

    /* 1일 소정근로 미달: "Xh Ym / Xh Ym 소정" */
    private String formatUnderMinHour(AttendanceAdminRow r, int max) {
        long workedMin = (r.getCheckInAt() != null && r.getCheckOutAt() != null)
                ? Duration.between(r.getCheckInAt(), r.getCheckOutAt()).toMinutes() : 0L;
        long scheduledMin = (r.getGroupStartTime() != null && r.getGroupEndTime() != null)
                ? Duration.between(r.getGroupStartTime(), r.getGroupEndTime()).toMinutes() : 0L;
        return String.format("%s / %s 소정", formatHm(workedMin), formatHm(scheduledMin));
    }

    /* 미승인 초과근무: "Xh Ym 초과 (미승인)" */
    private String formatUnapprovedOt(AttendanceAdminRow r, int max) {
        long overMin = (r.getCheckOutAt() != null && r.getGroupEndTime() != null)
                ? Duration.between(r.getGroupEndTime(), r.getCheckOutAt().toLocalTime()).toMinutes()
                : 0L;
        return String.format("%s 초과 (미승인)", formatHm(Math.max(0, overMin)));
    }

    /* 주간 최대근무시간 초과: "사원주간(h) / 정책(h) 정책" */
    private String formatMaxHourExceed(AttendanceAdminRow r, int max) {
        long weekMin = (r.getWeekWorkedMinutes() != null) ? r.getWeekWorkedMinutes() : 0L;
        return String.format("%dh / %dh 정책", weekMin / 60, max / 60);
    }
}
