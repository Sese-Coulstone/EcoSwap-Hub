package com.ecoswap.userservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponse {
    //@JsonProperty("access_token")
    private String accessToken;

    //@JsonProperty("refresh_token")
    private String refreshToken;

    //@JsonProperty("expires_in")
    private Long expiresIn;

    //@JsonProperty("refresh_expires_in")
    private Long refreshExpiresIn;

    //@JsonProperty("token_type")
    private String tokenType;

    //@JsonProperty("user_info")
    private AuthResponse userInfo;
}
