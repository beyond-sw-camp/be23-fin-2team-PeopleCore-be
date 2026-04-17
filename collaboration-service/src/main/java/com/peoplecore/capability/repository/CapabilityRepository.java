package com.peoplecore.capability.repository;

import com.peoplecore.capability.entity.Capability;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CapabilityRepository extends JpaRepository<Capability, String> {
    List<Capability> findByCategory(String category);
}
