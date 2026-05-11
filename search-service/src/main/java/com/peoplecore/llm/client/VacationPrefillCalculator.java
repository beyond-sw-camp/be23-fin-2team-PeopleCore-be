package com.peoplecore.llm.client;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 휴가 prefill 슬롯/시간 계산 헬퍼.
 * <p>
 * FE 의 {@code src/pages/attendance/components/vacationFormShared.ts} 와 1:1 동기화 —
 * LLM 이 보낸 (startDate, endDate, dayOption) 을 백엔드 진실의 원천인
 * {@code vacReqItems[] = {startAt, endAt, useDay}} 로 펼친다.
 * <p>
 * 분리한 이유:
 * <ul>
 *   <li>FE 비동기 effect 경합 제거 — 사용자가 채워진 양식 보고 바로 결재요청 눌러도
 *       BE 의 {@code VacationUseFormHandler.preCreate} 가 차단하지 않게.</li>
 *   <li>{@link com.peoplecore.llm.CopilotService}#executePrefillApprovalForm 의
 *       OPEN_APPROVAL_FORM payload 조립 시 한 줄 호출로 슬롯 배열을 만들기 위해.</li>
 * </ul>
 */
public final class VacationPrefillCalculator {

    /** FE DayOption 과 동기화된 정규형 enum. label 은 FE 가 표기에 그대로 쓰는 값과 같다. */
    public enum DayOption {
        FULL("종일", 1.0),
        HALF_AM("반차(전반)", 0.5),
        HALF_PM("반차(후반)", 0.5),
        QUARTER_1("반반차(1/4)", 0.25),
        QUARTER_2("반반차(2/4)", 0.25),
        QUARTER_3("반반차(3/4)", 0.25),
        QUARTER_4("반반차(4/4)", 0.25);

        public final String label;
        public final double useDay;

        DayOption(String label, double useDay) {
            this.label = label;
            this.useDay = useDay;
        }
    }

    private VacationPrefillCalculator() {}

    private static final DateTimeFormatter HMS = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** 한국 표준 9-to-6 폴백 — workGroup 응답이 없거나 파싱 실패 시 사용. */
    private static final LocalTime DEFAULT_START = LocalTime.of(9, 0);
    private static final LocalTime DEFAULT_END = LocalTime.of(18, 0);
    private static final LocalTime DEFAULT_BREAK_START = LocalTime.of(12, 0);
    private static final LocalTime DEFAULT_BREAK_END = LocalTime.of(13, 0);

    /** 일~토 표기. FE buildVacReqDatesTextFromSlots 와 동일한 인덱싱(0=일). */
    private static final String[] DAY_NAMES_KO = {"일", "월", "화", "수", "목", "금", "토"};

    /**
     * LLM 의 dayOption 토큰을 정규형 {@link DayOption} 으로 매핑.
     * FE {@code parseLlmDayOption} 와 동기화 — LLM tool enum("종일"/"오전반차"/"오후반차"/"반반차1~4")
     * 외에도 사용자 발화에서 흘러온 약식 표기를 함께 흡수한다.
     * <p>
     * 매칭 실패 시 {@link DayOption#FULL} 폴백 — LLM 이 dayOption 을 보내지 않으면 종일 휴가로 해석.
     */
    public static DayOption parseDayOption(String raw) {
        if (raw == null) return DayOption.FULL;
        String v = raw.replaceAll("\\s+", "").toLowerCase();
        if (v.isEmpty()) return DayOption.FULL;
        return switch (v) {
            case "종일", "하루", "풀데이", "fullday", "full" -> DayOption.FULL;
            case "오전반차", "반차(전반)", "반차전반", "전반", "오전", "am반차" -> DayOption.HALF_AM;
            case "오후반차", "반차(후반)", "반차후반", "후반", "오후", "pm반차" -> DayOption.HALF_PM;
            case "반반차1", "반반차(1/4)", "반반차1/4" -> DayOption.QUARTER_1;
            case "반반차2", "반반차(2/4)", "반반차2/4" -> DayOption.QUARTER_2;
            case "반반차3", "반반차(3/4)", "반반차3/4" -> DayOption.QUARTER_3;
            case "반반차4", "반반차(4/4)", "반반차4/4" -> DayOption.QUARTER_4;
            default -> DayOption.FULL;
        };
    }

    /**
     * 근무그룹 시간표 기준 옵션별 [시작, 종료] HH:mm:ss 윈도우 계산.
     * FE {@code computeOptionWindow} 와 동일한 로직 — 오전/오후 비대칭 대비해 각 구간을 독립적으로 2등분.
     * <p>
     * workGroup 이 null 이거나 시간 필드가 누락이면 폴백 시간표(09:00~18:00 / 12:00~13:00 점심) 사용.
     */
    public static String[] computeOptionWindow(Map<String, Object> workGroup, DayOption option) {
        LocalTime s = parseTime(workGroup, "startTime", DEFAULT_START);
        LocalTime e = parseTime(workGroup, "endTime", DEFAULT_END);
        LocalTime bs = parseTime(workGroup, "breakStart", DEFAULT_BREAK_START);
        LocalTime be = parseTime(workGroup, "breakEnd", DEFAULT_BREAK_END);

        int sMin = s.toSecondOfDay() / 60;
        int eMin = e.toSecondOfDay() / 60;
        int bsMin = bs.toSecondOfDay() / 60;
        int beMin = be.toSecondOfDay() / 60;
        int morningMid = (sMin + bsMin) / 2;
        int afternoonMid = (beMin + eMin) / 2;

        return switch (option) {
            case FULL -> new String[]{fmt(s), fmt(e)};
            case HALF_AM -> new String[]{fmt(s), fmt(bs)};
            case HALF_PM -> new String[]{fmt(be), fmt(e)};
            case QUARTER_1 -> new String[]{fmt(s), fmtMin(morningMid)};
            case QUARTER_2 -> new String[]{fmtMin(morningMid), fmt(bs)};
            case QUARTER_3 -> new String[]{fmt(be), fmtMin(afternoonMid)};
            case QUARTER_4 -> new String[]{fmtMin(afternoonMid), fmt(e)};
        };
    }

    /**
     * 시작~종료일(포함) 사이 모든 날짜에 동일 dayOption 으로 슬롯 펼침.
     * 슬롯 1개 = {@code {startAt, endAt, useDay}} (LocalDateTime ISO 문자열 + BigDecimal).
     * <p>
     * 잘못된 포맷(YYYY-MM-DD 가 아님) 이거나 endDate {@literal <} startDate 면 빈 리스트.
     * 호출자는 빈 리스트면 prefill 에 vacReqItems 를 넣지 말 것 — FE 가 자체 async 폴백으로 동작.
     */
    public static List<Map<String, Object>> expandSlots(
            String startDateRaw, String endDateRaw, DayOption option, Map<String, Object> workGroup) {
        if (startDateRaw == null || startDateRaw.isBlank()) return List.of();
        String startDate = startDateRaw.length() >= 10 ? startDateRaw.substring(0, 10) : startDateRaw;
        String endDateInput = (endDateRaw == null || endDateRaw.isBlank()) ? startDate
                : (endDateRaw.length() >= 10 ? endDateRaw.substring(0, 10) : endDateRaw);

        LocalDate start;
        LocalDate end;
        try {
            start = LocalDate.parse(startDate);
            end = LocalDate.parse(endDateInput);
        } catch (Exception e) {
            return List.of();
        }
        if (end.isBefore(start)) return List.of();

        String[] window = computeOptionWindow(workGroup, option);
        BigDecimal useDay = BigDecimal.valueOf(option.useDay);

        List<Map<String, Object>> slots = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            Map<String, Object> slot = new LinkedHashMap<>();
            slot.put("startAt", d + "T" + window[0]);
            slot.put("endAt", d + "T" + window[1]);
            slot.put("useDay", useDay);
            slots.add(slot);
        }
        return slots;
    }

    /**
     * 결재문서 "휴가 일자" 칸 표시용 문자열. FE {@code buildVacReqDatesTextFromSlots} 와 동일.
     * 각 슬롯을 "YYYY-MM-DD (요일) [옵션] HH:mm ~ HH:mm" 으로 줄바꿈 나열.
     * 종일 옵션은 옵션 라벨을 생략해 깔끔하게.
     */
    public static String buildDatesText(List<Map<String, Object>> slots, DayOption option) {
        if (slots == null || slots.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < slots.size(); i++) {
            Map<String, Object> slot = slots.get(i);
            String startAt = String.valueOf(slot.get("startAt"));
            String endAt = String.valueOf(slot.get("endAt"));
            if (startAt.length() < 16 || endAt.length() < 16) continue;
            String ymd = startAt.substring(0, 10);
            String startHm = startAt.substring(11, 16);
            String endHm = endAt.substring(11, 16);

            String dayName;
            try {
                // Java DayOfWeek: MON=1..SUN=7. FE JS getDay(): SUN=0..SAT=6 → DAY_NAMES_KO 인덱싱과 동일.
                int dow = LocalDate.parse(ymd).getDayOfWeek().getValue() % 7; // SUN=0, MON=1, ..., SAT=6
                dayName = DAY_NAMES_KO[dow];
            } catch (Exception ignored) {
                dayName = "";
            }

            String optLabel = (option != DayOption.FULL) ? " (" + option.label + ")" : "";
            if (i > 0) sb.append("\n");
            sb.append(ymd).append(" (").append(dayName).append(")").append(optLabel)
                    .append(" ").append(startHm).append(" ~ ").append(endHm);
        }
        return sb.toString();
    }

    /** 슬롯 useDay 합계. FE 가 표시용 vacReqUseDay 로 사용(상신 시점에선 BE 가 vacReqItems 합으로 재계산). */
    public static BigDecimal sumUseDay(List<Map<String, Object>> slots) {
        if (slots == null || slots.isEmpty()) return BigDecimal.ZERO;
        BigDecimal sum = BigDecimal.ZERO;
        for (Map<String, Object> slot : slots) {
            Object v = slot.get("useDay");
            if (v instanceof BigDecimal bd) sum = sum.add(bd);
            else if (v instanceof Number n) sum = sum.add(BigDecimal.valueOf(n.doubleValue()));
        }
        return sum;
    }

    private static LocalTime parseTime(Map<String, Object> wg, String key, LocalTime fallback) {
        if (wg == null) return fallback;
        Object v = wg.get(key);
        if (v == null) return fallback;
        try {
            String s = v.toString();
            // LocalTime.parse 는 "HH:mm" / "HH:mm:ss" 둘 다 수용.
            // "HH:mm:ss.SSS" 같은 정밀도 있는 경우 8자만 잘라 안전 처리.
            return LocalTime.parse(s.length() > 8 ? s.substring(0, 8) : s);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String fmt(LocalTime t) {
        return t.format(HMS);
    }

    private static String fmtMin(int totalMinutes) {
        int h = totalMinutes / 60;
        int m = totalMinutes % 60;
        return String.format("%02d:%02d:00", h, m);
    }
}
