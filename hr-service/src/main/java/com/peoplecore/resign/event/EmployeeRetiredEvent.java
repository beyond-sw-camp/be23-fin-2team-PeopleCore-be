package com.peoplecore.resign.event;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

// 사원 퇴직 처리 이벤트 (퇴직처리 -> 퇴직금 대장에 추가)
@Getter
public class EmployeeRetiredEvent {
    private final UUID companyId;
    private final Long empId;

    @Autowired
    public EmployeeRetiredEvent(UUID companyId, Long empId) {
        this.companyId = companyId;
        this.empId = empId;
    }
}
