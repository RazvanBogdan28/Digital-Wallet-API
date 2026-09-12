package com.razvan.digital_wallet_api.integration;

import com.razvan.digital_wallet_api.entity.User;
import com.razvan.digital_wallet_api.repository.TransactionRepository;
import com.razvan.digital_wallet_api.repository.UserRepository;
import com.razvan.digital_wallet_api.repository.WalletRepository;
import com.razvan.digital_wallet_api.service.JwtService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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
class TransactionControllerIntegrationTest {

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
    private WalletRepository walletRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void getTransactionsByWalletIdShouldReturnTransactionHistory()
            throws Exception {

        User user1 = new User(
                "Razvan",
                "Test",
                "history1@test.com",
                "password123"
        );

        User user2 = new User(
                "John",
                "Test",
                "history2@test.com",
                "password123"
        );

        user1 = userRepository.save(user1);
        user2 = userRepository.save(user2);

        String user1Token =
                jwtService.generateAccessToken(user1.getEmail());

        String user2Token =
                jwtService.generateAccessToken(user2.getEmail());

        String wallet1Body = """
                {
                  "userId": %d,
                  "currency": "EUR"
                }
                """.formatted(user1.getId());

        String wallet2Body = """
                {
                  "userId": %d,
                  "currency": "EUR"
                }
                """.formatted(user2.getId());

        String wallet1Response = mockMvc.perform(
                        post("/api/wallets")
                                .header(
                                        "Authorization",
                                        "Bearer " + user1Token
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(wallet1Body)
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String wallet2Response = mockMvc.perform(
                        post("/api/wallets")
                                .header(
                                        "Authorization",
                                        "Bearer " + user2Token
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(wallet2Body)
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long wallet1Id = objectMapper
                .readTree(wallet1Response)
                .get("id")
                .asLong();

        Long wallet2Id = objectMapper
                .readTree(wallet2Response)
                .get("id")
                .asLong();

        mockMvc.perform(
                        post("/api/wallets/" + wallet1Id + "/deposit")
                                .header(
                                        "Authorization",
                                        "Bearer " + user1Token
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "amount": 100.00
                                        }
                                        """)
                )
                .andExpect(status().isOk());

        String transferBody = """
                {
                  "toWalletId": %d,
                  "amount": 25.00,
                  "description": "History integration test"
                }
                """.formatted(wallet2Id);

        mockMvc.perform(
                        post("/api/wallets/" + wallet1Id + "/transfer")
                                .header(
                                        "Authorization",
                                        "Bearer " + user1Token
                                )
                                .header(
                                        "Idempotency-Key",
                                        "history-test-key"
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(transferBody)
                )
                .andExpect(status().isOk());

        mockMvc.perform(
                        get("/api/transactions/wallet/" + wallet1Id)
                                .header(
                                        "Authorization",
                                        "Bearer " + user1Token
                                )
                                .param("page", "0")
                                .param("size", "10")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(
                        jsonPath("$.content[0].fromWalletId")
                                .value(wallet1Id)
                )
                .andExpect(
                        jsonPath("$.content[0].toWalletId")
                                .value(wallet2Id)
                )
                .andExpect(
                        jsonPath("$.content[0].amount")
                                .value(25.00)
                )
                .andExpect(
                        jsonPath("$.content[0].currency")
                                .value("EUR")
                )
                .andExpect(
                        jsonPath("$.content[0].type")
                                .value("TRANSFER")
                )
                .andExpect(
                        jsonPath("$.content[0].status")
                                .value("COMPLETED")
                )
                .andExpect(
                        jsonPath("$.content[0].description")
                                .value("History integration test")
                )
                .andExpect(
                        jsonPath("$.totalElements")
                                .value(1)
                );
    }

    @Test
    void getTransactionsByWalletIdShouldReturn404WhenWalletNotFound()
            throws Exception {

        User user = new User(
                "Razvan",
                "Test",
                "notfound@test.com",
                "password123"
        );

        user = userRepository.save(user);

        String token =
                jwtService.generateAccessToken(user.getEmail());

        mockMvc.perform(
                        get("/api/transactions/wallet/999999")
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                                .param("page", "0")
                                .param("size", "10")
                )
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.error")
                                .value("WALLET_NOT_FOUND")
                );
    }
}