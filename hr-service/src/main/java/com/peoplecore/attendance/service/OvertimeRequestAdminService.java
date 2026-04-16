package com.peoplecore.attendance.service;

import com.peoplecore.attendance.dto.OvertimeRequestAdminRowResDto;
import com.peoplecore.attendance.dto.PagedResDto;
import com.peoplecore.attendance.entity.OtStatus;
import com.peoplecore.attendance.entity.OvertimeRequest;
import com.peoplecore.attendance.repository.OvertimeRequestAdminQueryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/* 초과근무 관리(관리자) 화면 조회 서비스.
 * 4개 탭(전체/승인대기/승인완료/반려) 모두 같은 조회 로직을 공유 — 컨트롤러가 status 만 다르게 넘김 */
@Service
@Transactional(readOnly = true)
public class OvertimeRequestAdminService {

    private final OvertimeRequestAdminQueryRepository queryRepository;

    @Autowired
    public OvertimeRequestAdminService(OvertimeRequestAdminQueryRepository queryRepository) {
        this.queryRepository = queryRepository;
    }

    /* status null → 전체 탭, 그 외 → 해당 상태만 페이징 */
    public PagedResDto<OvertimeRequestAdminRowResDto> getRequests(UUID companyId, OtStatus status, int page, int size) {
        List<OvertimeRequest> rows = queryRepository.findPage(companyId, status, page, size);
        long total = queryRepository.countAll(companyId, status);

        List<OvertimeRequestAdminRowResDto> content = rows.stream()
                .map(this::toDto)
                .toList();

        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) total / size);

        return PagedResDto.<OvertimeRequestAdminRowResDto>builder()
                .content(content)
                .page(page)
                .size(size)
                .totalElements(total)
                .totalPages(totalPages)
                .build();
    }

    /* 엔티티 → 행 DTO 변환. fetch join 으로 LAZY 호출 비용 없음 */
    private OvertimeRequestAdminRowResDto toDto(OvertimeRequest o) {
        long minutes = Duration.between(o.getOtPlanStart(), o.getOtPlanEnd()).toMinutes();
        return OvertimeRequestAdminRowResDto.builder()
                .otId(o.getOtId())
                .empId(o.getEmployee().getEmpId())
                .empName(o.getEmployee().getEmpName())
                .deptName(o.getEmployee().getDept().getDeptName())
                .otType(classifyOtType(o.getOtPlanStart(), o.getOtPlanEnd()))
                .otDate(o.getOtDate().toLocalDate())
                .durationLabel(formatDuration(minutes))
                .durationMinutes(minutes)
                .otReason(o.getOtReason())
                .otStatus(o.getOtStatus())
                .approvalDocId(o.getApprovalDocId())
                .build();
    }

    /* 근무 유형 분류
     *  1) 토/일 시작 → 휴일근무
     *  2) 종료 22:00 이후 또는 자정 넘김 → 야간근무
     *  3) 그 외 → 연장근무
     *  공휴일은 HolidayLookup 연동 시 추후 보강 */
    private String classifyOtType(LocalDateTime start, LocalDateTime end) {
        DayOfWeek dow = start.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) return "휴일근무";

        boolean crossesMidnight = !end.toLocalDate().isEqual(start.toLocalDate());
        if (crossesMidnight) return "야간근무";

        LocalTime endTime = end.toLocalTime();
        if (!endTime.isBefore(LocalTime.of(22, 0))) return "야간근무"; // 22:00 이후 종료
        return "연장근무";
    }

    /* "Xh YYm" 라벨 — 예) 150 → "2h 30m" */
    private String formatDuration(long minutes) {
        long h = minutes / 60;
        long m = minutes % 60;
        return String.format("%dh %02dm", h, m);
    }
}
