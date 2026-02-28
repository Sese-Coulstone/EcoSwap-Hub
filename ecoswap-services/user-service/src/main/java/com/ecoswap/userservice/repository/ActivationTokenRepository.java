package com.ecoswap.userservice.repository;

import com.ecoswap.userservice.entity.ActivationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ActivationTokenRepository extends JpaRepository<ActivationToken, String> {
    Optional<ActivationToken> findByToken(String token);
    Optional<ActivationToken> findByEmail(String email);
    void deleteByEmail(String email);
}
