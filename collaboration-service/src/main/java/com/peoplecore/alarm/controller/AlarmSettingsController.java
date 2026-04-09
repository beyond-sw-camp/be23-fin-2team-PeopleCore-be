package com.peoplecore.alarm.controller;

import com.peoplecore.alarm.dto.AlarmSettingsReqDto;
import com.peoplecore.alarm.dto.AlarmSettingsResDto;
import com.peoplecore.alarm.service.AlarmSettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/alarm/settings")
public class AlarmSettingsController {

    private final AlarmSettingsService alarmSettingsService;
    @Autowired
    public AlarmSettingsController(AlarmSettingsService alarmSettingsService) {
        this.alarmSettingsService = alarmSettingsService;
    }

//    내 알림 설정조회
    @GetMapping
    public ResponseEntity<List<AlarmSettingsResDto>> getMySettings(
            @RequestHeader("X-User-Company")UUID companyId,
            @RequestHeader("X-User-Id") Long empId){
        return ResponseEntity.ok(alarmSettingsService.getMySettings(companyId, empId));
    }

//    알림 설정 변경
    @PatchMapping
    public ResponseEntity<AlarmSettingsResDto> updateSetting(
            @RequestHeader("X-User-Company")UUID companyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestBody AlarmSettingsReqDto reqDto){
        return ResponseEntity.ok(alarmSettingsService.updateSetting(companyId, empId, reqDto));
    }
            )

}
