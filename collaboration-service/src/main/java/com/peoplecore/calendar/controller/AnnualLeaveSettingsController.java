package com.peoplecore.calendar.controller;

import com.peoplecore.calendar.dtos.AnnualLeaveSettingReqDto;
import com.peoplecore.calendar.dtos.AnnualLeaveSettingResDto;
import com.peoplecore.calendar.service.AnnualLeaveSettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/calendar/settings")
public class AnnualLeaveSettingsController {

    private final AnnualLeaveSettingsService annualLeaveSettingsService;

    @Autowired
    public AnnualLeaveSettingsController(AnnualLeaveSettingsService annualLeaveSettingsService) {
        this.annualLeaveSettingsService = annualLeaveSettingsService;
    }


    //    연차 연동 설정 조회
    @GetMapping("/annual-leave")
    public ResponseEntity<AnnualLeaveSettingResDto> getAnnualLeaveSettings(
            @RequestHeader("X-User-Company") UUID componyId,
            @RequestHeader("X-User-Id") Long empId){
        return ResponseEntity.ok(annualLeaveSettingsService.getSettings(componyId,empId));
    }

//    연차 연동 설정 저장
    @PostMapping("/annual-leave")
    public ResponseEntity<AnnualLeaveSettingResDto> saveAnnualLeaveSettings(
            @RequestHeader("X-User-Company") UUID componyId,
            @RequestHeader("X-User-Id") Long empId,
            @RequestBody AnnualLeaveSettingReqDto reqDto){
        return ResponseEntity.ok(annualLeaveSettingsService.saveSettings(componyId,empId, reqDto));
    }

}
