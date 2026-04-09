package com.peoplecore.calendar.repository;


import com.peoplecore.calendar.entity.Events;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventsRepository extends JpaRepository<Events, Long>, EventsCustomRepository {

}
