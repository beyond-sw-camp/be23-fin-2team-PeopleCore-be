package com.peoplecore.calendar.service;

import com.peoplecore.calendar.dtos.AnnualLeaveSettingReqDto;
import com.peoplecore.calendar.dtos.AnnualLeaveSettingResDto;
import com.peoplecore.calendar.entity.MyCalendars;
import com.peoplecore.calendar.repository.AnnualLeaveSettingRepository;
import com.peoplecore.calendar.repository.MyCalendarsRepository;
import com.peoplecore.exception.CustomException;
import com.peoplecore.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AnnualLeaveSettingsService {

    private final AnnualLeaveSettingRepository annualLeaveSettingRepository;
    private final MyCalendarsRepository myCalendarsRepository;

    @Autowired
    public AnnualLeaveSettingsService(AnnualLeaveSettingRepository annualLeaveSettingRepository, MyCalendarsRepository myCalendarsRepository) {
        this.annualLeaveSettingRepository = annualLeaveSettingRepository;
        this.myCalendarsRepository = myCalendarsRepository;
    }

//    연차연동 설정 조회
    public AnnualLeaveSettingResDto getSettings(UUID companyId, Long empId ){
        List<AnnualLeaveSetting> settings = annualLeaveSettingRepository.findByCompanyIdAndEmpId(companyId, empId);

        if (settings.isEmpty()){
            return AnnualLeaveSettingResDto.builder()
                    .calendars(List.of())
                    .build();
        }

        return AnnualLeaveSettingResDto.fromEntity(settings);
    }

//    연차연동 설정 저장(기존 삭제 후 재설정)
    @Transactional
    public AnnualLeaveSettingResDto saveSettings(UUID companyId, Long empId, AnnualLeaveSettingReqDto reqDto){

//        기존 설정 삭제
        annualLeaveSettingRepository.deleteByCompanyIdAndEmpId(companyId, empId);

//        새설정 저장
        List<AnnualLeaveSetting> newSettings = reqDto.getCalendars().stream()
                .map(item -> {
                    MyCalendars myCal = myCalendarsRepository.findById(item.getCalendarId())
                            .orElseThrow(()-> new CustomException(ErrorCode.CALENDAR_NOT_FOUND));
                    return AnnualLeaveSetting.builder()
                            .empId(empId)
                            .companyId(companyId)
                            .myCalendar(myCal)
                            .isPublic(item.getIsPublic())
                            .build();
                    })
                .toList();

        annualLeaveSettingRepository.saveAll(newSettings);

        return AnnualLeaveSettingResDto.fromEntity(newSettings);
    }
}
