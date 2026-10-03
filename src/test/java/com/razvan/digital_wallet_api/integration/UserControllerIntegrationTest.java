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
import static org.junit.jupiter.api.Assertions.assertEquals;

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
    @Test
    void createUserShouldRejectFirstNameLongerThan255() throws Exception {
        CreateUserRequest request = validRegistrationRequest();
        request.setFirstName("a".repeat(256));

        mockMvc.perform(
                        post("/api/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("First name must not exceed 255 characters"));

        assertEquals(0L, userRepository.count());
    }

    @Test
    void createUserShouldRejectLastNameLongerThan255() throws Exception {
        CreateUserRequest request = validRegistrationRequest();
        request.setLastName("a".repeat(256));

        mockMvc.perform(
                        post("/api/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("Last name must not exceed 255 characters"));

        assertEquals(0L, userRepository.count());
    }

    @Test
    void createUserShouldRejectEmailLongerThan255() throws Exception {
        CreateUserRequest request = validRegistrationRequest();
        request.setEmail(boundaryEmail(59));

        assertEquals(256, request.getEmail().length());

        mockMvc.perform(
                        post("/api/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

        assertEquals(0L, userRepository.count());
    }

    @Test
    void createUserShouldAcceptFieldsAt255CharacterLimit() throws Exception {
        CreateUserRequest request = validRegistrationRequest();
        request.setFirstName("a".repeat(255));
        request.setLastName("b".repeat(255));
        request.setEmail(boundaryEmail(58));

        assertEquals(255, request.getEmail().length());

        mockMvc.perform(
                        post("/api/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.firstName")
                        .value(request.getFirstName()))
                .andExpect(jsonPath("$.lastName")
                        .value(request.getLastName()))
                .andExpect(jsonPath("$.email")
                        .value(request.getEmail()))
                .andExpect(jsonPath("$.password").doesNotExist());

        assertEquals(1L, userRepository.count());

        User saved = userRepository.findByEmail(request.getEmail())
                .orElseThrow();

        assertEquals(request.getFirstName(), saved.getFirstName());
        assertEquals(request.getLastName(), saved.getLastName());
    }

    private CreateUserRequest validRegistrationRequest() {
        CreateUserRequest request = new CreateUserRequest();
        request.setFirstName("Razvan");
        request.setLastName("Test");
        request.setEmail("validation@test.com");
        request.setPassword("password123");
        return request;
    }

    private String boundaryEmail(int finalLabelLength) {
        return "a".repeat(64)
                + "@"
                + "b".repeat(63)
                + "."
                + "c".repeat(63)
                + "."
                + "d".repeat(finalLabelLength)
                + ".com";
    }
    @Test
    void getCurrentUserShouldReturnOwnProfileAndUserRole()
            throws Exception {
        User user = createUser("me-user@test.com", Role.USER);
        createUser("me-other@test.com", Role.USER);

        String token =
                jwtService.generateAccessToken(user.getEmail());

        mockMvc.perform(
                        get("/api/users/me")
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.id")
                                .value(user.getId().intValue())
                )
                .andExpect(
                        jsonPath("$.email")
                                .value(user.getEmail())
                )
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void getCurrentUserShouldReturnAdminRole() throws Exception {
        User admin = createUser("me-admin@test.com", Role.ADMIN);

        String token =
                jwtService.generateAccessToken(admin.getEmail());

        mockMvc.perform(
                        get("/api/users/me")
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.id")
                                .value(admin.getId().intValue())
                )
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void getCurrentUserShouldReturn401WithoutAuthentication()
            throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }
}