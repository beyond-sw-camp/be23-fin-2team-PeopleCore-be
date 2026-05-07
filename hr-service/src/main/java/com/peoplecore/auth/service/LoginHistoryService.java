package com.peoplecore.auth.service;

import com.peoplecore.auth.dto.LoginHistoryDto;
import com.peoplecore.auth.repository.LoginHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LoginHistoryService {
    private final LoginHistoryRepository loginHistoryRepositoryrepository;

    @Autowired
    public LoginHistoryService(LoginHistoryRepository loginHistoryRepositoryrepository) {
        this.loginHistoryRepositoryrepository = loginHistoryRepositoryrepository;
    }

    @Transactional(readOnly = true)
    public List<LoginHistoryDto> list(Long empId, int limit) {
        return loginHistoryRepositoryrepository.findByEmpIdOrderByLoginAtDesc(empId)
                .stream()
                .map(LoginHistoryDto::from)
                .toList();
    }
}

