package com.ecoswap.userservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    @Value("${brevo.api-key}")
    private String apiKey;

    @Value("${brevo.api-url}")
    private String apiUrl;

    @Value("${brevo.sender-email}")
    private String senderEmail;

    @Value("${brevo.sender-name}")
    private String senderName;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    private final WebClient.Builder webClientBuilder;

    public void sendActivationEmail(String toEmail, String username, String activationToken) {
        String activationLink = frontendUrl + "/activate?token=" + activationToken;

        Map<String, Object> emailPayload = Map.of(
                "sender", Map.of(
                        "email", senderEmail,
                        "name", senderName
                ),
                "to", new Object[]{
                        Map.of("email", toEmail, "name", username)
                },
                "subject", "Activate Your EcoSwap-Hub Account",
                "htmlContent", buildActivationEmailTemplate(username, activationLink)
        );

        sendEmail(emailPayload);
    }

    public void sendPasswordResetEmail(String toEmail, String username, String resetToken) {
        String resetLink = frontendUrl + "/reset-password?token=" + resetToken;

        Map<String, Object> emailPayload = Map.of(
                "sender", Map.of(
                        "email", senderEmail,
                        "name", senderName
                ),
                "to", new Object[]{
                        Map.of("email", toEmail, "name", username)
                },
                "subject", "Reset Your EcoSwap-Hub Password",
                "htmlContent", buildPasswordResetEmailTemplate(username, resetLink)
        );

        sendEmail(emailPayload);
    }

    private void sendEmail(Map<String, Object> emailPayload) {
        // Log API key presence (not the actual key for security)
        log.debug("Brevo API key configured: {}", apiKey != null && !apiKey.isEmpty() ? "Yes (length: " + apiKey.length() + ")" : "No");

        WebClient webClient = webClientBuilder
                .baseUrl(apiUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("api-key", apiKey)
                .build();

        webClient.post()
                .uri("/smtp/email")
                .bodyValue(emailPayload)
                .retrieve()
                .onStatus(
                        status -> status.is4xxClientError() || status.is5xxServerError(),
                        response -> response.bodyToMono(String.class)
                                .map(body -> {
                                    log.error("Brevo API error response: {} - Body: {}", response.statusCode(), body);
                                    return new RuntimeException("Brevo API error: " + response.statusCode() + " - " + body);
                                })
                )
                .bodyToMono(String.class)
                .doOnSuccess(response -> log.info("Email sent successfully: {}", response))
                .doOnError(error -> log.error("Error sending email: {}", error.getMessage()))
                .subscribe();
    }

    private String buildActivationEmailTemplate(String username, String activationLink) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .button { 
                        display: inline-block; 
                        padding: 12px 24px; 
                        background-color: #007bff; 
                        color: #ffffff; 
                        text-decoration: none; 
                        border-radius: 5px; 
                        margin: 20px 0;
                    }
                    .footer { margin-top: 30px; font-size: 12px; color: #666; }
                </style>
            </head>
            <body>
                <div class="container">
                    <h2>Welcome to EcoSwap-Hub, %s!</h2>
                    <p>Thank you for registering. Please activate your account by clicking the button below:</p>
                    <a href="%s" class="button">Activate Account</a>
                    <p>Or copy and paste this link into your browser:</p>
                    <p style="word-break: break-all;">%s</p>
                    <p>This link will expire in 24 hours.</p>
                    <div class="footer">
                        <p>If you didn't create this account, please ignore this email.</p>
                        <p>© 2025 EcoSwap-Hub. All rights reserved.</p>
                    </div>
                </div>
            </body>
            </html>
            """, username, activationLink, activationLink);
    }

    private String buildPasswordResetEmailTemplate(String username, String resetLink) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .button { 
                        display: inline-block; 
                        padding: 12px 24px; 
                        background-color: #dc3545; 
                        color: #ffffff; 
                        text-decoration: none; 
                        border-radius: 5px; 
                        margin: 20px 0;
                    }
                    .footer { margin-top: 30px; font-size: 12px; color: #666; }
                </style>
            </head>
            <body>
                <div class="container">
                    <h2>Password Reset Request</h2>
                    <p>Hi %s,</p>
                    <p>We received a request to reset your password. Click the button below to reset it:</p>
                    <a href="%s" class="button">Reset Password</a>
                    <p>Or copy and paste this link into your browser:</p>
                    <p style="word-break: break-all;">%s</p>
                    <p>This link will expire in 24 hours.</p>
                    <div class="footer">
                        <p>If you didn't request this, please ignore this email.</p>
                        <p>© 2025 EcoSwap-Hub. All rights reserved.</p>
                    </div>
                </div>
            </body>
            </html>
            """, username, resetLink, resetLink);
    }
}
