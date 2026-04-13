package com.peoplecore.attendance.dto;

// DTO 컨벤션 (@Data + 4대 Lombok, final 금지)
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 카드 드릴다운 응답 행.
 *
 * 사용처:
 *  - GET /attendance/admin/daily/card  → 특정 카드 타입(LATE 등) 에 해당하는 사원 목록.
 *
 * 필드:
 *  - 사원 기본정보 (empId, empNum, empName, deptName, gradeName)
 *  - weeklyWorkedMinutes : 주간(월~일) 누적 근무 분 — 숫자 원본
 *  - weeklyWorkedText    : "Xh Ym" 포맷 문자열 — 프론트 편의
 *  - detail              : 카드 타입별 상세 문구 (Service.formatDetail 이 조합)
 */
@Data                 // getter/setter/toString/equals/hashCode 생성
@NoArgsConstructor    // 빈 생성자 (프레임워크 편의)
@AllArgsConstructor   // 전체 필드 생성자 (테스트/수동 조립)
@Builder              // 서비스 조립 시 가독성 + null 안전
public class AttendanceDailyCardRowResDto {

    /** 사원 PK */
    private Long empId;

    /** 사번 */
    private String empNum;

    /** 사원명 */
    private String empName;

    /** 부서명 */
    private String deptName;

    /** 직급명 */
    private String gradeName;

    /** 주간 누적 근무 분 (월~일 범위, checkOut 찍힌 것만 합산) */
    private Long weeklyWorkedMinutes;

    /** 주간 누적 근무 텍스트 ("Xh Ym") — 프론트 그대로 표기용 */
    private String weeklyWorkedText;

    /**
     * 카드 타입별 상세 문구.
     * 예)
     *  - LATE            → "09:12 출근 (12분 지각)"
     *  - EARLY_LEAVE     → "17:30 퇴근 (30분 조퇴)"
     *  - MAX_HOUR_EXCEED → "55h / 52h 정책"
     *  - OFFSITE         → IP 주소 또는 "근무지 외 체크인"
     */
    private String detail;
}
