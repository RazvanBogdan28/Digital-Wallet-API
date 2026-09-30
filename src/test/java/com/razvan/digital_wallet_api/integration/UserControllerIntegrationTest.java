package com.razvan.digital_wallet_api.integration;

import com.razvan.digital_wallet_api.dto.CreateUserRequest;
import com.razvan.digital_wallet_api.entity.Role;
import com.razvan.digital_wallet_api.entity.User;
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
class UserControllerIntegrationTest {

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
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    private User createUser(String email, Role role) {
        User user = new User(
                "Razvan",
                "Test",
                email,
                passwordEncoder.encode("password123")
        );

        user.setRole(role);

        return userRepository.save(user);
    }

    @Test
    void createUserShouldReturn201() throws Exception {
        CreateUserRequest request = new CreateUserRequest();
        request.setFirstName("Razvan");
        request.setLastName("Test");
        request.setEmail("integration@test.com");
        request.setPassword("password123");

        mockMvc.perform(
                        post("/api/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.firstName").value("Razvan"))
                .andExpect(jsonPath("$.lastName").value("Test"))
                .andExpect(jsonPath("$.email").value("integration@test.com"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void createUserShouldReturn409WhenEmailAlreadyExists()
            throws Exception {
        CreateUserRequest request = new CreateUserRequest();
        request.setFirstName("Razvan");
        request.setLastName("Test");
        request.setEmail("duplicate@test.com");
        request.setPassword("password123");

        String body = objectMapper.writeValueAsString(request);

        mockMvc.perform(
                        post("/api/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isCreated());

        mockMvc.perform(
                        post("/api/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.error").value("USER_ALREADY_EXISTS")
                );
    }

    @Test
    void getAllUsersShouldReturn403ForNormalUser() throws Exception {
        User user = createUser("normal@test.com", Role.USER);
        String token = jwtService.generateAccessToken(user.getEmail());

        mockMvc.perform(
                        get("/api/users")
                                .header("Authorization", "Bearer " + token)
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ACCESS_DENIED"));
    }

    @Test
    void getAllUsersShouldReturn200ForAdmin() throws Exception {
        User admin = createUser("admin@test.com", Role.ADMIN);
        String token = jwtService.generateAccessToken(admin.getEmail());

        mockMvc.perform(
                        get("/api/users")
                                .header("Authorization", "Bearer " + token)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value(admin.getEmail()))
                .andExpect(jsonPath("$[0].password").doesNotExist());
    }

    @Test
    void getUserByIdShouldReturn200ForOwnProfile() throws Exception {
        User user = createUser("own@test.com", Role.USER);
        String token = jwtService.generateAccessToken(user.getEmail());

        mockMvc.perform(
                        get("/api/users/{id}", user.getId())
                                .header("Authorization", "Bearer " + token)
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.id").value(user.getId().intValue())
                )
                .andExpect(jsonPath("$.email").value(user.getEmail()))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void getUserByIdShouldReturn403ForAnotherUsersProfile()
            throws Exception {
        User requester = createUser("requester@test.com", Role.USER);
        User otherUser = createUser("other@test.com", Role.USER);

        String token =
                jwtService.generateAccessToken(requester.getEmail());

        mockMvc.perform(
                        get("/api/users/{id}", otherUser.getId())
                                .header("Authorization", "Bearer " + token)
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.firstName").doesNotExist())
                .andExpect(jsonPath("$.lastName").doesNotExist());
    }

    @Test
    void getUserByIdShouldReturn200ForAdminReadingAnotherProfile()
            throws Exception {
        User admin = createUser("profile-admin@test.com", Role.ADMIN);
        User otherUser = createUser("profile-other@test.com", Role.USER);

        String token = jwtService.generateAccessToken(admin.getEmail());

        mockMvc.perform(
                        get("/api/users/{id}", otherUser.getId())
                                .header("Authorization", "Bearer " + token)
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.id").value(otherUser.getId().intValue())
                )
                .andExpect(jsonPath("$.email").value(otherUser.getEmail()))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void getUserByIdShouldReturn401WithoutAuthentication()
            throws Exception {
        User user = createUser("unauthenticated@test.com", Role.USER);

        mockMvc.perform(
                        get("/api/users/{id}", user.getId())
                )
                .andExpect(status().isUnauthorized());
    }
}