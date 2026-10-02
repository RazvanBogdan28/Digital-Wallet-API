package com.razvan.digital_wallet_api.integration;

import com.razvan.digital_wallet_api.entity.RefreshToken;
import com.razvan.digital_wallet_api.entity.Role;
import com.razvan.digital_wallet_api.entity.User;
import com.razvan.digital_wallet_api.repository.RefreshTokenRepository;
import com.razvan.digital_wallet_api.repository.UserRepository;
import com.razvan.digital_wallet_api.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    }

    private User createUser(String email) {
        User user = new User(
                "Razvan",
                "Test",
                email,
                passwordEncoder.encode("password123")
        );

        user.setRole(Role.USER);

        return userRepository.save(user);
    }

    private LocalDateTime expirationOf(String token) {
        return LocalDateTime.ofInstant(
                jwtService.extractExpiration(token).toInstant(),
                ZoneOffset.UTC
        );
    }

    @Test
    void loginShouldReturnAccessAndRefreshToken() throws Exception {
        createUser("auth@test.com");

        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "email": "auth@test.com",
                                          "password": "password123"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    void refreshShouldReturnNewAccessToken() throws Exception {
        User user = createUser("refresh@test.com");

        String token =
                jwtService.generateRefreshToken(user.getEmail());

        refreshTokenRepository.save(
                new RefreshToken(
                        token,
                        expirationOf(token),
                        false,
                        user
                )
        );

        mockMvc.perform(
                        post("/api/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "refreshToken": "%s"
                                        }
                                        """.formatted(token))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void refreshTokenShouldNotAccessProtectedEndpoint()
            throws Exception {
        User user = createUser("refresh-protected@test.com");

        String token =
                jwtService.generateRefreshToken(user.getEmail());

        mockMvc.perform(
                        get("/api/wallets/1")
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutShouldRevokeRefreshToken() throws Exception {
        User user = createUser("logout@test.com");

        String token =
                jwtService.generateRefreshToken(user.getEmail());

        refreshTokenRepository.save(
                new RefreshToken(
                        token,
                        expirationOf(token),
                        false,
                        user
                )
        );

        String requestBody = """
                {
                  "refreshToken": "%s"
                }
                """.formatted(token);

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

    @Test
    void loginShouldStoreRefreshExpirationFromJwt()
            throws Exception {
        User user = createUser("expiration@test.com");

        Long originalExpiration =
                (Long) ReflectionTestUtils.getField(
                        jwtService,
                        "refreshExpiration"
                );

        try {
            ReflectionTestUtils.setField(
                    jwtService,
                    "refreshExpiration",
                    2L * 60 * 60 * 1000
            );

            LocalDateTime beforeLogin =
                    LocalDateTime.now(ZoneOffset.UTC).withNano(0);

            String response = mockMvc.perform(
                            post("/api/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {
                                              "email": "%s",
                                              "password": "password123"
                                            }
                                            """.formatted(user.getEmail()))
                    )
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            LocalDateTime afterLogin =
                    LocalDateTime.now(ZoneOffset.UTC).withNano(0);

            String token = objectMapper.readTree(response)
                    .get("refreshToken")
                    .asText();

            RefreshToken stored =
                    refreshTokenRepository.findByToken(token)
                            .orElseThrow();

            LocalDateTime jwtExpiration = expirationOf(token);

            assertEquals(
                    jwtExpiration,
                    stored.getExpiresAt()
            );

            assertFalse(
                    jwtExpiration.isBefore(beforeLogin.plusHours(2)),
                    "Expiration must be at least two hours after login began"
            );

            assertFalse(
                    jwtExpiration.isAfter(afterLogin.plusHours(2)),
                    "Expiration must be at most two hours after login finished"
            );
        } finally {
            ReflectionTestUtils.setField(
                    jwtService,
                    "refreshExpiration",
                    originalExpiration
            );
        }
    }

    @Test
    void emailShouldBeCaseInsensitiveForRegistrationAndLogin()
            throws Exception {

        mockMvc.perform(
                        post("/api/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "firstName": "Ana",
                                          "lastName": "Test",
                                          "email": "Ana.Case@Test.com",
                                          "password": "password123"
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(
                        jsonPath("$.email").value("ana.case@test.com")
                );

        mockMvc.perform(
                        post("/api/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "firstName": "Ana",
                                          "lastName": "Duplicate",
                                          "email": "ANA.CASE@TEST.COM",
                                          "password": "password123"
                                        }
                                        """)
                )
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.error").value("USER_ALREADY_EXISTS")
                );

        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "email": "ANA.CASE@TEST.COM",
                                          "password": "password123"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        assertEquals(1L, userRepository.count());
    }
}