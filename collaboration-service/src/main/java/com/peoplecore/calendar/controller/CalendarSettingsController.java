package com.peoplecore.calendar.controller;

import com.peoplecore.calendar.dtos.AnnualLeaveSettingReqDto;
import com.peoplecore.calendar.dtos.AnnualLeaveSettingResDto;
import com.peoplecore.calendar.service.SettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/calendar/settings")
public class CalendarSettingsController {

    private final SettingsService settingsService;

    @Autowired
    public CalendarSettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;1
    }


    //    연차 연동 설정 조회
    @GetMapping("/annual-leave")
    public ResponseEntity<AnnualLeaveSettingResDto> getAnnualLeaveSettings(
            @RequestHeader("/X-User-Company") UUID componyId,
            @RequestHeader("X-User-Id") Long empId){
        return ResponseEntity.ok(settingsService.getSettings(componyId,empId));
    }

//    연차 연동 설정 저장
    @PostMapping("/annual-leave")
    public ResponseEntity<AnnualLeaveSettingResDto> saveAnnualLeaveSettings(
            @RequestHeader("/X-User-Company") UUID componyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestBody AnnualLeaveSettingReqDto reqDto){
        return ResponseEntity.ok(settingsService.saveSettings(componyId,empId, reqDto));
    }

}
