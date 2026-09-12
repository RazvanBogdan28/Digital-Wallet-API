package com.razvan.digital_wallet_api.integration;

import com.razvan.digital_wallet_api.dto.CreateUserRequest;
import com.razvan.digital_wallet_api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import com.razvan.digital_wallet_api.entity.Role;
import com.razvan.digital_wallet_api.service.JwtService;
import com.razvan.digital_wallet_api.entity.User;

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

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
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
                .andExpect(jsonPath("$.email").value("integration@test.com"));
    }

    @Test
    void createUserShouldReturn409WhenEmailAlreadyExists() throws Exception {

        CreateUserRequest request = new CreateUserRequest();
        request.setFirstName("Razvan");
        request.setLastName("Test");
        request.setEmail("duplicate@test.com");
        request.setPassword("password123");

        mockMvc.perform(
                        post("/api/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isCreated());

        mockMvc.perform(
                        post("/api/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("USER_ALREADY_EXISTS"));
    }

    @Test
    void getAllUsersShouldReturn403ForNormalUser() throws Exception {

        User user = new User(
                "Razvan",
                "Test",
                "normal@test.com",
                "password123"
        );

        user.setRole(Role.USER);
        user = userRepository.save(user);

        String token =
                jwtService.generateAccessToken(user.getEmail());

        mockMvc.perform(
                        get("/api/users")
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void getAllUsersShouldReturn200ForAdmin() throws Exception {

        User admin = new User(
                "Admin",
                "Test",
                "admin@test.com",
                "password123"
        );

        admin.setRole(Role.ADMIN);
        admin = userRepository.save(admin);

        String token =
                jwtService.generateAccessToken(admin.getEmail());

        mockMvc.perform(
                        get("/api/users")
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                )
                .andExpect(status().isOk());
    }
}