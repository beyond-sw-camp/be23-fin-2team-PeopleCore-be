package com.peoplecore.alarm.service;

import com.peoplecore.alarm.dto.AlarmSettingsReqDto;
import com.peoplecore.alarm.dto.AlarmSettingsResDto;
import com.peoplecore.alarm.repository.AlarmSettingsRepository;
import com.peoplecore.entity.AlarmSettings;
import com.peoplecore.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AlarmSettingsService {

//    지원하는 서비스 목록
    private static final List<String> SUPPORTED_SERVICES =  List.of("Approval", "Calendar", "Board", "Attendance", "HR");

    private final AlarmSettingsRepository alarmSettingsRepository;

    @Autowired
    public AlarmSettingsService(AlarmSettingsRepository alarmSettingsRepository) {
        this.alarmSettingsRepository = alarmSettingsRepository;
    }

//    내 알림 설정 전체 조회 - 설정이 없는 서비스는 기본값(true)
public List<AlarmSettingsResDto> getMySettings(UUID companyId, Long empId) {
    List<AlarmSettings> existing = alarmSettingsRepository.findByCompanyIdAndEmpId(companyId, empId);

    // 이미 설정된 서비스 이름 수집
    List<String> existingServices = existing.stream()
            .map(AlarmSettings::getService)
            .toList();

    // 설정이 없는 서비스는 기본값으로 생성
    List<AlarmSettingsResDto> defaults = SUPPORTED_SERVICES.stream()
            .filter(svc -> !existingServices.contains(svc))
            .map(svc -> AlarmSettingsResDto.builder()
                    .service(svc)
                    .emailEnabled(true)
                    .pushEnabled(true)
                    .popupEnabled(true)
                    .build())
            .toList();

    // 기존 + 기본값 합쳐서 반환
    List<AlarmSettingsResDto> result = new java.util.ArrayList<>(
            existing.stream().map(AlarmSettingsResDto::fromEntity).toList());
    result.addAll(defaults);

    return result;
}


//    알림 설정 변경 — 없으면 생성, 있으면 수정
    @Transactional
    public AlarmSettingsResDto updateSetting(UUID companyId, Long empId,
                                               AlarmSettingsReqDto request) {
        if (!SUPPORTED_SERVICES.contains(request.getService())) {
            throw new BusinessException("지원하지 않는 서비스입니다: " + request.getService(),
                    HttpStatus.BAD_REQUEST);
        }

        AlarmSettings settings = alarmSettingsRepository
                .findByCompanyIdAndEmpIdAndService(companyId, empId, request.getService())
                .orElse(null);

        if (settings == null) {
            // 신규 생성
            settings = AlarmSettings.builder()
                    .companyId(companyId)
                    .empId(empId)
                    .service(request.getService())
                    .emailEnabled(request.getEmailEnabled() != null ? request.getEmailEnabled() : true)
                    .pushEnabled(request.getPushEnabled() != null ? request.getPushEnabled() : true)
                    .popupEnabled(request.getPopupEnabled() != null ? request.getPopupEnabled() : true)
                    .build();
        } else {
            // 기존 수정
            settings.update(request.getEmailEnabled(), request.getPushEnabled(), request.getPopupEnabled());
        }

        alarmSettingsRepository.save(settings);
        return AlarmSettingsResDto.fromEntity(settings);
    }
}
