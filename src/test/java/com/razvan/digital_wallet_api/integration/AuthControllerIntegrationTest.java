package com.razvan.digital_wallet_api.integration;

import com.razvan.digital_wallet_api.entity.Role;
import com.razvan.digital_wallet_api.entity.User;
import com.razvan.digital_wallet_api.repository.UserRepository;
import com.razvan.digital_wallet_api.service.JwtService;
import com.razvan.digital_wallet_api.entity.RefreshToken;
import com.razvan.digital_wallet_api.repository.RefreshTokenRepository;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:17-alpine")
                    .withDatabaseName("digital_wallet_test")
                    .withUsername("test")
                    .withPassword("test");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void loginShouldReturnAccessAndRefreshToken() throws Exception {

        User user = new User(
                "Razvan",
                "Test",
                "auth@test.com",
                passwordEncoder.encode("password123")
        );

        user.setRole(Role.USER);

        userRepository.save(user);

        String requestBody = """
                {
                  "email": "auth@test.com",
                  "password": "password123"
                }
                """;

        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists());
    }

    @Test
    void refreshShouldReturnNewAccessToken() throws Exception {

        User user = new User(
                "Razvan",
                "Test",
                "refresh@test.com",
                passwordEncoder.encode("password123")
        );

        user.setRole(Role.USER);

        user = userRepository.save(user);

        String refreshToken =
                jwtService.generateRefreshToken(user.getEmail());

        RefreshToken storedRefreshToken =
                new RefreshToken(
                        refreshToken,
                        LocalDateTime.now().plusDays(7),
                        false,
                        user
                );

        refreshTokenRepository.save(storedRefreshToken);

        String requestBody = """
                {
                  "refreshToken": "%s"
                }
                """.formatted(refreshToken);

        mockMvc.perform(
                        post("/api/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test
    void refreshTokenShouldNotAccessProtectedEndpoint()
            throws Exception {

        User user = new User(
                "Razvan",
                "Test",
                "refresh-protected@test.com",
                passwordEncoder.encode("password123")
        );

        user.setRole(Role.USER);

        user = userRepository.save(user);

        String refreshToken =
                jwtService.generateRefreshToken(user.getEmail());

        mockMvc.perform(
                        get("/api/wallets/1")
                                .header(
                                        "Authorization",
                                        "Bearer " + refreshToken
                                )
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutShouldRevokeRefreshToken() throws Exception {

        User user = new User(
                "Razvan",
                "Test",
                "logout@test.com",
                passwordEncoder.encode("password123")
        );

        user.setRole(Role.USER);
        user = userRepository.save(user);

        String refreshToken =
                jwtService.generateRefreshToken(user.getEmail());

        RefreshToken storedRefreshToken =
                new RefreshToken(
                        refreshToken,
                        LocalDateTime.now().plusDays(7),
                        false,
                        user
                );

        refreshTokenRepository.save(storedRefreshToken);

        String requestBody = """
            {
              "refreshToken": "%s"
            }
            """.formatted(refreshToken);

        mockMvc.perform(
                        post("/api/auth/logout")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isNoContent());

        mockMvc.perform(
                        post("/api/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isUnauthorized())
                .andExpect(
                        jsonPath("$.error")
                                .value("INVALID_CREDENTIALS")
                );
    }
}