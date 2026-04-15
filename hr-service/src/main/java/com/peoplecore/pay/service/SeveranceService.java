package com.peoplecore.pay.service;

import com.peoplecore.pay.dtos.SeveranceCalcReqDto;
import com.peoplecore.pay.dtos.SeveranceDetailResDto;
import com.peoplecore.pay.repository.SeveranceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class SeveranceService {

    private final SeveranceRepository severanceRepository;

    @Autowired
    public SeveranceService(SeveranceRepository severanceRepository) {
        this.severanceRepository = severanceRepository;
    }


// 퇴직금 산정
    public SeveranceDetailResDto calculateSeverance(UUID companyId, SeveranceCalcReqDto reqDto){

    }




}


