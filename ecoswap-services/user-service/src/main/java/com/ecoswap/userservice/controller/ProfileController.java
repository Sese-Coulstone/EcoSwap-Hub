package com.ecoswap.userservice.controller;

import com.ecoswap.userservice.dto.*;
import com.ecoswap.userservice.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ProfileController {
    private final ProfileService profileService;

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody AuthRequest request) {
        try {
            AuthResponse response = profileService.registerProfile(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "success", true,
                    "message", "Registration successful. Please check your email for activation link.",
                    "data", response
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/activate/{token}")
    public ResponseEntity<Map<String, Object>> activateAccount(@PathVariable String token) {
        try {
            LoginResponse response = profileService.activateAccount(token);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Account activated successfully!",
                    "data", response
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        try {
            LoginResponse response = profileService.login(request);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Login successful",
                    "data", response
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<Map<String, Object>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        try {
            LoginResponse response = profileService.refreshAccessToken(request.getRefreshToken());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Token refreshed successfully",
                    "data", response
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/{email}")
    public ResponseEntity<AuthResponse> getProfile(@PathVariable String email) {
        AuthResponse response = profileService.getProfile(email);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/fix-account")
    public ResponseEntity<Map<String, Object>> fixAccount(@RequestParam String email) {
        try {
            profileService.fixUserAccount(email);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Account fixed successfully. You can now login."
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<Map<String, Object>> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        try {
            profileService.requestPasswordReset(request.getEmail());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Password reset email sent. Please check your email for instructions."
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Map<String, Object>> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        try {
            profileService.confirmPasswordReset(request.getToken(), request.getNewPassword());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Password reset successfully. You can now login with your new password."
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }
}
