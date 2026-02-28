package com.ecoswap.userservice.service;

import com.ecoswap.userservice.dto.AuthRequest;
import com.ecoswap.userservice.dto.AuthResponse;
import com.ecoswap.userservice.dto.LoginRequest;
import com.ecoswap.userservice.dto.LoginResponse;
import com.ecoswap.userservice.entity.ActivationToken;
import com.ecoswap.userservice.entity.PasswordResetToken;
import com.ecoswap.userservice.entity.Profile;
import com.ecoswap.userservice.repository.ActivationTokenRepository;
import com.ecoswap.userservice.repository.PasswordResetTokenRepository;
import com.ecoswap.userservice.repository.ProfileRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileService {
    private final ProfileRepository repository;
    private final KeycloakService keycloakService;
    private final ActivationTokenRepository activationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailService emailService;

    @Value("${app.activation-token-expiry}")
    private long activationTokenExpiryMillis; // 24 hours in milliseconds (86400000)

    @Transactional
    public AuthResponse registerProfile(AuthRequest request) {
        // Check if email already exists
        if (repository.existsByEmail(request.getEmail())) {
            throw new UsernameNotFoundException("Email already exists");
        }

        if (keycloakService.userExistsInKeycloak(request.getEmail())) {
            throw new RuntimeException("User already exists in Keycloak");
        }

        try {
            // Create user in Keycloak
            String keycloakId = keycloakService.createUser(
                    request.getUsername(),
                    request.getEmail(),
                    request.getPassword()
            );

            // Create user in Database
            Profile profile = Profile.builder()
                    .keycloakId(keycloakId)
                    .username(request.getUsername())
                    .email(request.getEmail())
                    .isActive(false)
                    .emailVerified(false)
                    .build();

            profile = repository.save(profile);

            // Generate activation token and send email
            String activationToken = generateActivationToken(profile.getEmail());

            emailService.sendActivationEmail(
                    profile.getEmail(),
                    profile.getUsername(),
                    activationToken
            );
            log.info("User registered successfully: {}", profile.getEmail());

            return mapToProfileResponse(profile);

        } catch (Exception e) {
            log.error("Registration failed for user {}: {}", request.getEmail(), e.getMessage(), e);
            throw new RuntimeException("Registration failed: " + e.getMessage());
        }
    }

    @Transactional
    public LoginResponse activateAccount(String token) {
        ActivationToken activationToken = activationTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid activation token"));

        if (activationToken.getUsed()) {
            throw new RuntimeException("Activation token already used");
        }

        if (activationToken.isExpired()) {
            throw new RuntimeException("Activation token has expired");
        }

        Profile profile = repository.findByEmail(activationToken.getEmail())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        // Enable user in Keycloak
        keycloakService.enableUser(profile.getKeycloakId());

        // Update profile in Database
        profile.setIsActive(true);
        profile.setEmailVerified(true);
        repository.save(profile);

        // Mark token as used
        activationToken.setUsed(true);
        activationTokenRepository.save(activationToken);

        log.info("Account activated successfully: {}", profile.getEmail());


        // Return success response without tokens (user needs to login separately)
        return LoginResponse.builder()
                .userInfo(mapToProfileResponse(profile))
                .build();
    }

    public LoginResponse login(LoginRequest request) {
        try {
            // The "username" field in LoginRequest can be either username or email
            // First, try to get user from database
            Profile profile = repository.findByEmail(request.getUsername())
                    .or(() -> repository.findByUsername(request.getUsername()))
                    .orElseThrow(() -> new UsernameNotFoundException("User not found"));

            // Check if account is active
            if (!profile.getIsActive()) {
                throw new RuntimeException("Account is not activated. Please check your email.");
            }

            // Authenticate with Keycloak using EMAIL (since we use email as username in Keycloak)
            Map<String, Object> tokens = keycloakService.authenticateUser(
                    profile.getEmail(),  // ← Use EMAIL for Keycloak authentication
                    request.getPassword()
            );

            log.info("User logged in successfully: {}", profile.getEmail());

            return LoginResponse.builder()
                    .accessToken((String) tokens.get("access_token"))
                    .refreshToken((String) tokens.get("refresh_token"))
                    .expiresIn(((Number) tokens.get("expires_in")).longValue())
                    .refreshExpiresIn(((Number) tokens.get("refresh_expires_in")).longValue())
                    .tokenType((String) tokens.get("token_type"))
                    .userInfo(mapToProfileResponse(profile))
                    .build();

        } catch (Exception e) {
            log.error("Login failed for user {}: {}", request.getUsername(), e.getMessage());
            throw new RuntimeException("Login failed: " + e.getMessage());
        }
    }

    public AuthResponse getProfile(String email) {
        Profile profile = repository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
        return mapToProfileResponse(profile);
    }

    public LoginResponse refreshAccessToken(String refreshToken) {
        try {
            // Refresh token with Keycloak
            Map<String, Object> tokenResponse = keycloakService.refreshToken(refreshToken);

            // Build login response (without user info since we don't need to query DB)
            LoginResponse response = LoginResponse.builder()
                    .accessToken((String) tokenResponse.get("access_token"))
                    .refreshToken((String) tokenResponse.get("refresh_token"))
                    .expiresIn(((Number) tokenResponse.get("expires_in")).longValue())
                    .refreshExpiresIn(((Number) tokenResponse.get("refresh_expires_in")).longValue())
                    .tokenType((String) tokenResponse.get("token_type"))
                    .build();

            log.info("Token refreshed successfully");

            return response;

        } catch (Exception e) {
            log.error("Token refresh error: {}", e.getMessage(), e);
            throw new RuntimeException("Token refresh failed: " + e.getMessage());
        }
    }

    public Boolean userExists(String keycloakId) {
        return repository.existsByKeycloakId(keycloakId);
    }

    @Transactional
    public void fixUserAccount(String email) {
        try {
            // Verify user exists in database
            Profile profile = repository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found in database"));

            // Fix user in Keycloak
            keycloakService.fixUserAccount(email);

            // Update database if needed
            if (!profile.getIsActive() || !profile.getEmailVerified()) {
                profile.setIsActive(true);
                profile.setEmailVerified(true);
                repository.save(profile);
                log.info("Updated user profile in database: {}", email);
            }

            log.info("User account fixed successfully: {}", email);

        } catch (Exception e) {
            log.error("Error fixing user account: {}", e.getMessage(), e);
            throw new RuntimeException("Error fixing user account: " + e.getMessage());
        }
    }

    private AuthResponse mapToProfileResponse(Profile profile) {
        return AuthResponse.builder()
                .userId(profile.getId())
                .keycloakId(profile.getKeycloakId())
                .username(profile.getUsername())
                .email(profile.getEmail())
                .isActive(profile.getIsActive())
                .emailVerified(profile.getEmailVerified())
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }

    private String generateActivationToken(String email) {
        String token = UUID.randomUUID().toString();

        // Convert milliseconds to hours
        long expiryHours = activationTokenExpiryMillis / (1000 * 60 * 60); // Convert ms to hours

        ActivationToken activationToken = ActivationToken.builder()
                .token(token)
                .email(email)
                .expiryDate(LocalDateTime.now().plusHours(expiryHours))
                .used(false)
                .build();

        activationTokenRepository.save(activationToken);
        return token;
    }

    /**
     * Request password reset - sends email with reset token
     */
    @Transactional
    public void requestPasswordReset(String email) {
        try {
            // Check if user exists in database
            Profile profile = repository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found with email: " + email));

            // Check if user exists in Keycloak
            if (!keycloakService.userExistsInKeycloak(email)) {
                throw new RuntimeException("User not found in authentication system");
            }

            // Generate password reset token
            String resetToken = generatePasswordResetToken(email);

            // Send password reset email
            emailService.sendPasswordResetEmail(email, profile.getUsername(), resetToken);

            log.info("Password reset email sent to: {}", email);

        } catch (Exception e) {
            log.error("Error requesting password reset for {}: {}", email, e.getMessage(), e);
            throw new RuntimeException("Error requesting password reset: " + e.getMessage());
        }
    }

    /**
     * Confirm password reset - validates token and resets password
     */
    @Transactional
    public void confirmPasswordReset(String token, String newPassword) {
        try {
            // Validate token
            PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                    .orElseThrow(() -> new RuntimeException("Invalid or expired reset token"));

            if (resetToken.getUsed()) {
                throw new RuntimeException("Reset token has already been used");
            }

            if (resetToken.isExpired()) {
                throw new RuntimeException("Reset token has expired");
            }

            // Reset password in Keycloak
            keycloakService.resetPassword(resetToken.getEmail(), newPassword);

            // Mark token as used
            resetToken.setUsed(true);
            passwordResetTokenRepository.save(resetToken);

            log.info("Password reset successfully for user: {}", resetToken.getEmail());

        } catch (Exception e) {
            log.error("Error confirming password reset: {}", e.getMessage(), e);
            throw new RuntimeException("Error resetting password: " + e.getMessage());
        }
    }

    private String generatePasswordResetToken(String email) {
        String token = UUID.randomUUID().toString();

        // Convert milliseconds to hours (reuse activation token expiry)
        long expiryHours = activationTokenExpiryMillis / (1000 * 60 * 60);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(token)
                .email(email)
                .expiryDate(LocalDateTime.now().plusHours(expiryHours))
                .used(false)
                .build();

        passwordResetTokenRepository.save(resetToken);
        return token;
    }
}
