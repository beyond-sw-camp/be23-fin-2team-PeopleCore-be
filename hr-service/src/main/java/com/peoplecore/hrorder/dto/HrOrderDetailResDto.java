package com.peoplecore.hrorder.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class HrOrderDetailResDto {
    private Long orderId;                   // 발령 ID
    private Long empId;                     // 대상 사원 ID
    private String empNum;
    private String empName;
    private String deptName;
    private String gradeName;
    private String titleName;
    private String orderType;               // 발령유형 (PROMOTION/TRANSFER/TITLE_CHANGE)
    private String effectiveDate;           // 발령일
    private String status;                  // 상태 (PENDING/CONFIRMED/REJECTED/APPLIED)
    private boolean isNotified;             // 통보 여부
    private String notifiedAt;              // 통보 일시 (null이면 미통보)
    private String createdAt;               // 등록일
    private List<DetailInfo> details;       // 변경 전/후 목록
    private List<FieldDetail> formFields;   // 동적 폼 필드 (snapshot틀 + values값)

    // 변경 상세 1건: id를 이름으로 변환
    // ex. targetType=DEPARTMENT, beforeName="개발팀", afterName="기획팀"
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DetailInfo {
        private String targetType;          // 변경 구분 (GRADE/DEPARTMENT/TITLE)
        private String beforeName;          // 변경 전 이름
        private String afterName;           // 변경 후 이름
    }

    // 동적 폼 필드 1건
    // formSnapshot(등록 당시 폼 설정)에서 틀을, formValues(입력값)에서 값을 가져와 매칭
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FieldDetail {
        private String fieldKey;            // 필드 식별자 (ex. orderTitle)
        private String label;               // 화면 표시명 (ex. 발령제목) <- snapshot
        private String section;             // 소속 섹션 (ex. 발령 기본 정보) <- snapshot
        private String fieldType;           // 입력방식 (ex. text) <- snapshot
        private String value;               // 저장된 값 (ex. 2026년 상반기 인사발령) <- values
    }
}
