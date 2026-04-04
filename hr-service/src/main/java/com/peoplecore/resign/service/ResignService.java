package com.peoplecore.resign.service;

import com.peoplecore.resign.repository.ResignRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ResignService {

    private final ResignRepository resignRepository;

    public ResignService(ResignRepository resignRepository) {
        this.resignRepository = resignRepository;
    }
}
