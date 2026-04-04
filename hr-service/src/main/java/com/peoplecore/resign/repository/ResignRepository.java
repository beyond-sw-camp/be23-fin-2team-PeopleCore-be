package com.peoplecore.resign.repository;

import com.peoplecore.resign.domain.Resign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ResignRepository extends JpaRepository<Resign, Long> {

}
