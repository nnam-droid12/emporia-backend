package com.emporia.backend.repository;

import com.emporia.backend.model.SMEProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SMEProfileRepository extends JpaRepository<SMEProfile, UUID> {
    Optional<SMEProfile> findByPhoneNumber(String phoneNumber);
}