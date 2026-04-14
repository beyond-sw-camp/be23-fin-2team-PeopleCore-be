package com.peoplecore.attendance.entity;

public final class OtTypeFlag {
// 초과근무유형 TODO :

    public static final int OVERTIME = 1;   // 연장근로 (bit 0)
    public static final int NIGHT    = 2;   // 야간근로 (bit 1)
    public static final int HOLIDAY  = 4;   // 휴일근로 (bit 2)

    private OtTypeFlag() {}

    public static boolean hasOvertime(int flag) { return (flag & OVERTIME) != 0; }
    public static boolean hasNight(int flag)    { return (flag & NIGHT) != 0; }
    public static boolean hasHoliday(int flag)  { return (flag & HOLIDAY) != 0; }

    /** 비트마스크 → 한글 라벨 (예: "연장+야간") */
    public static String toLabel(int flag) {
        StringBuilder sb = new StringBuilder();
        if (hasOvertime(flag)) sb.append("연장");
        if (hasNight(flag))    { if (!sb.isEmpty()) sb.append("+"); sb.append("야간"); }
        if (hasHoliday(flag))  { if (!sb.isEmpty()) sb.append("+"); sb.append("휴일"); }
        return sb.isEmpty() ? "일반" : sb.toString();
    }
}
