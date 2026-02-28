package com.ecoswap.userservice.repository;

import com.ecoswap.userservice.entity.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProfileRepository extends JpaRepository<Profile, UUID> {
    Boolean existsByKeycloakId(String keycloakId);
    Optional<Profile> findByEmail(String email);
    Boolean existsByEmail(String email);
    Optional<Profile> findByKeycloakId(String keycloakId);
    Optional<Profile> findByUsername(String username);
}
