package com.peoplecore.vacation.repository;

import com.peoplecore.vacation.entity.VacationRemainder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VacationRemainderRepository extends JpaRepository<VacationRemainder,  Long> {

//    사원의 해당연도 연차 잔여 조회
     Optional<VacationRemainder> findByCompanyIdAndEmpIdAndVacRemYear(UUID companyIs, Long empId, Integer year);
}
