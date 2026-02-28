package com.ecoswap.userservice.service;

import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakService {
    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.resource-client-id}")
    private String resourceClientId;

    @Value("${keycloak.resource-client-secret}")
    private String resourceClientSecret;

    @Value("${keycloak.token-uri}")
    private String tokenUri;

    private final Keycloak keycloak; // This uses admin-client-id and admin-client-secret from KeycloakConfig

    /**
     * Create user in Keycloak (disabled by default, requires activation)
     * NOTE: We use EMAIL as username in Keycloak for consistency with OAuth2 password grant flow
     */
    public String createUser(String username, String email, String password) {
        try {
            RealmResource realmResource = keycloak.realm(realm);
            UsersResource usersResource = realmResource.users();

            UserRepresentation user = new UserRepresentation();
            user.setUsername(email);  // ← Use EMAIL as username for Keycloak authentication
            user.setEmail(email);
            user.setFirstName(username); // Store display name in firstName
            user.setEnabled(false); // User is disabled until email verification
            user.setEmailVerified(false);
            user.setCredentials(Collections.singletonList(createPasswordCredential(password)));

            // Clear any required actions that might cause "not fully set up" error
            user.setRequiredActions(List.of()); // Empty list - no required actions

            Response response = usersResource.create(user);

            if (response.getStatus() == Response.Status.CREATED.getStatusCode()) {
                String userId = extractUserIdFromLocation(response.getLocation());
                log.info("User created in Keycloak with ID: {}", userId);
                return userId;
            } else {
                String error = response.readEntity(String.class);
                log.error("Failed to create user in Keycloak. Status: {}, Error: {}", response.getStatus(), error);
                throw new RuntimeException("Failed to create user in Keycloak: " + error);
            }

        } catch (Exception e) {
            log.error("Error creating user in Keycloak: {}", e.getMessage(), e);
            throw new RuntimeException("Error creating user in Keycloak: " + e.getMessage());
        }
    }

    /**
     * Enable user after email verification
     */
    public void enableUser(String keycloakId) {
        try {
            RealmResource realmResource = keycloak.realm(realm);
            UserResource userResource = realmResource.users().get(keycloakId);

            UserRepresentation user = userResource.toRepresentation();
            user.setEnabled(true);
            user.setEmailVerified(true);

            userResource.update(user);
            log.info("User enabled successfully: {}", keycloakId);

            // Update the user
            userResource.update(user);
        } catch (Exception e) {
            log.error("Error enabling user in Keycloak: {}", e.getMessage(), e);
            throw new RuntimeException("Error enabling user in Keycloak: " + e.getMessage());
        }
    }

    /**
     * Direct grant flow for server-side authentication
     * NOTE: username parameter should be the EMAIL (since we use email as username in Keycloak)
     */
    public Map<String, Object> authenticateUser(String email, String password) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<>();
            requestBody.add("grant_type", "password");
            requestBody.add("client_id", resourceClientId);
            requestBody.add("client_secret", resourceClientSecret);
            requestBody.add("username", email);  // ← Use EMAIL as username for Keycloak
            requestBody.add("password", password);
            requestBody.add("scope", "openid profile email");

            log.debug("Authenticating user with email: {} using client: {}", email, resourceClientId);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    tokenUri,
                    HttpMethod.POST,
                    request,
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                log.info("User authenticated successfully: {}", email);
                return response.getBody();
            } else {
                throw new RuntimeException("Authentication failed");
            }

        } catch (HttpClientErrorException e) {
            log.error("Authentication error for user {}: {} - {}", email, e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Invalid credentials");
        } catch (Exception e) {
            log.error("Authentication error for user {}: {}", email, e.getMessage());
            throw new RuntimeException("Invalid credentials");
        }
    }

    /**
     * Check if user exists in Keycloak by email
     */
    public boolean userExistsInKeycloak(String email) {
        try {
            RealmResource realmResource = keycloak.realm(realm);
            List<UserRepresentation> users = realmResource.users().searchByEmail(email, true);
            return !users.isEmpty();
        } catch (Exception e) {
            log.error("Error checking user existence in Keycloak: {}", e.getMessage(), e);
            return false;
        }
    }

    private CredentialRepresentation createPasswordCredential(String password) {
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(false);
        return credential;
    }

    private String extractUserIdFromLocation(java.net.URI location) {
        String path = location.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }


    public Map<String, Object> refreshToken(String refreshToken) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> requestBody = new LinkedMultiValueMap<>();
            requestBody.add("grant_type", "refresh_token");
            requestBody.add("client_id", resourceClientId);
            requestBody.add("client_secret", resourceClientSecret);
            requestBody.add("refresh_token", refreshToken);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    tokenUri,
                    HttpMethod.POST,
                    request,
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                log.info("Token refreshed successfully");
                return response.getBody();
            } else {
                throw new RuntimeException("Token refresh failed");
            }

        } catch (HttpClientErrorException e) {
            log.error("Token refresh error: {}", e.getMessage());
            throw new RuntimeException("Invalid or expired refresh token");
        } catch (Exception e) {
            log.error("Error during token refresh: {}", e.getMessage(), e);
            throw new RuntimeException("Token refresh error: " + e.getMessage());
        }
    }

    /**
     * Get user by email
     */
    public UserRepresentation getUserByEmail(String email) {
        try {
            RealmResource realmResource = keycloak.realm(realm);
            List<UserRepresentation> users = realmResource.users().searchByEmail(email, true);

            if (users.isEmpty()) {
                throw new RuntimeException("User not found with email: " + email);
            }

            return users.get(0);
        } catch (Exception e) {
            log.error("Error fetching user by email: {}", e.getMessage());
            throw new RuntimeException("Error fetching user: ");
        }
    }

    public void deleteUser(String keycloakId) {
        try {
            RealmResource realmResource = keycloak.realm(realm);
            realmResource.users().delete(keycloakId);
            log.info("User deleted from Keycloak: {}", keycloakId);
        } catch (Exception e) {
            log.error("Error deleting user from Keycloak: {}", e.getMessage(), e);
            throw new RuntimeException("Error deleting user from Keycloak: " + e.getMessage());
        }
    }

    // New method to get user by username
    public UserRepresentation getUserByUsername(String username) {
        try {
            RealmResource realmResource = keycloak.realm(realm);
            List<UserRepresentation> users = realmResource.users().searchByUsername(username, true);

            if (users.isEmpty()) {
                throw new RuntimeException("User not found with username: " + username);
            }

            return users.get(0);
        } catch (Exception e) {
            log.error("Error fetching user by username: {}", e.getMessage(), e);
            throw new RuntimeException("Error fetching user: " + e.getMessage());
        }
    }

    /**
     * Fix existing users who have "Account is not fully set up" issue
     * This method ensures all required fields are set and required actions are cleared
     */
    public void fixUserAccount(String email) {
        try {
            RealmResource realmResource = keycloak.realm(realm);
            List<UserRepresentation> users = realmResource.users().searchByEmail(email, true);

            if (users.isEmpty()) {
                throw new RuntimeException("User not found with email: " + email);
            }

            UserRepresentation user = users.get(0);
            UserResource userResource = realmResource.users().get(user.getId());

            // Fix user account - ensure everything is properly set
            user.setEnabled(true);
            user.setEmailVerified(true);
            user.setRequiredActions(List.of()); // Clear all required actions

            // Ensure required attributes are set
            if (user.getLastName() == null || user.getLastName().isEmpty()) {
                user.setLastName("");
            }
            if (user.getFirstName() == null || user.getFirstName().isEmpty()) {
                user.setFirstName(user.getUsername());
            }

            userResource.update(user);
            log.info("Fixed user account for: {} - Cleared required actions and ensured all fields are set", email);

        } catch (Exception e) {
            log.error("Error fixing user account for {}: {}", email, e.getMessage(), e);
            throw new RuntimeException("Error fixing user account: " + e.getMessage());
        }
    }

    /**
     * Reset user password in Keycloak
     */
    public void resetPassword(String email, String newPassword) {
        try {
            RealmResource realmResource = keycloak.realm(realm);
            List<UserRepresentation> users = realmResource.users().searchByEmail(email, true);

            if (users.isEmpty()) {
                throw new RuntimeException("User not found with email: " + email);
            }

            UserRepresentation user = users.get(0);
            UserResource userResource = realmResource.users().get(user.getId());

            // Create new password credential
            CredentialRepresentation credential = createPasswordCredential(newPassword);

            // Reset the password
            userResource.resetPassword(credential);

            log.info("Password reset successfully for user: {}", email);

        } catch (Exception e) {
            log.error("Error resetting password for user {}: {}", email, e.getMessage(), e);
            throw new RuntimeException("Error resetting password: " + e.getMessage());
        }
    }
}
