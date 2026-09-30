package com.razvan.digital_wallet_api.integration;

import com.razvan.digital_wallet_api.entity.Currency;
import com.razvan.digital_wallet_api.entity.User;
import com.razvan.digital_wallet_api.entity.Wallet;
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

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class WalletControllerIntegrationTest {

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
    void createWalletShouldReturn201() throws Exception {

        User user = new User(
                "Razvan",
                "Test",
                "email@test.com",
                "password123"
        );

        user = userRepository.save(user);

        String token =
                jwtService.generateAccessToken(user.getEmail());

        String requestBody = """
                {
                  "userId": %d,
                  "currency": "EUR"
                }
                """.formatted(user.getId());

        mockMvc.perform(
                        post("/api/wallets")
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.userId").value(user.getId()))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.balance").value(0));
    }

    @Test
    void createWalletShouldReturn409WhenWalletAlreadyExists()
            throws Exception {

        User user = new User(
                "Razvan",
                "Test",
                "email@test.com",
                "password123"
        );

        user = userRepository.save(user);

        String token =
                jwtService.generateAccessToken(user.getEmail());

        String requestBody = """
                {
                  "userId": %d,
                  "currency": "EUR"
                }
                """.formatted(user.getId());

        mockMvc.perform(
                        post("/api/wallets")
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isCreated());

        mockMvc.perform(
                        post("/api/wallets")
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.error")
                                .value("WALLET_ALREADY_EXISTS")
                );
    }

    @Test
    void depositShouldIncreaseWalletBalance() throws Exception {

        User user = new User(
                "Razvan",
                "Test",
                "email@test.com",
                "password123"
        );

        user = userRepository.save(user);

        String token =
                jwtService.generateAccessToken(user.getEmail());

        String createWalletBody = """
                {
                  "userId": %d,
                  "currency": "EUR"
                }
                """.formatted(user.getId());

        String walletResponse = mockMvc.perform(
                        post("/api/wallets")
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createWalletBody)
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long walletId = objectMapper
                .readTree(walletResponse)
                .get("id")
                .asLong();

        String depositBody = """
                {
                  "amount": 25.00
                }
                """;

        mockMvc.perform(
                        post("/api/wallets/" + walletId + "/deposit")
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(depositBody)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(walletId))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.balance").value(25.00));
    }

    @Test
    void transferShouldMoveMoneyBetweenWallets()
            throws Exception {

        User user1 = new User(
                "Razvan",
                "Test",
                "transfer1@test.com",
                "password123"
        );

        User user2 = new User(
                "John",
                "Test",
                "transfer2@test.com",
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
                  "description": "Integration transfer"
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
                                        "integration-transfer-key"
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(transferBody)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(wallet1Id))
                .andExpect(jsonPath("$.balance").value(75.00));

        var destinationWallet = walletRepository
                .findById(wallet2Id)
                .orElseThrow();

        assertEquals(
                new BigDecimal("25.00"),
                destinationWallet.getBalance()
        );
    }
    @Test
    void getWalletShouldReturn403WhenWalletBelongsToAnotherUser()
            throws Exception {

        User user1 = new User(
                "Razvan",
                "Test",
                "owner@test.com",
                "password123"
        );

        User user2 = new User(
                "John",
                "Test",
                "attacker@test.com",
                "password123"
        );

        user1 = userRepository.save(user1);
        user2 = userRepository.save(user2);

        String user1Token =
                jwtService.generateAccessToken(user1.getEmail());

        String user2Token =
                jwtService.generateAccessToken(user2.getEmail());

        String walletBody = """
            {
              "userId": %d,
              "currency": "EUR"
            }
            """.formatted(user1.getId());

        String walletResponse = mockMvc.perform(
                        post("/api/wallets")
                                .header(
                                        "Authorization",
                                        "Bearer " + user1Token
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(walletBody)
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long walletId = objectMapper
                .readTree(walletResponse)
                .get("id")
                .asLong();

        mockMvc.perform(
                        post("/api/wallets/" + walletId + "/deposit")
                                .header(
                                        "Authorization",
                                        "Bearer " + user2Token
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                        {
                          "amount": 25.00
                        }
                        """)
                )
                .andExpect(status().isForbidden());
    }
    @Test
    void getTransactionsShouldReturn403WhenWalletBelongsToAnotherUser()
            throws Exception {

        User user1 = new User(
                "Razvan",
                "Test",
                "owner-history@test.com",
                "password123"
        );

        User user2 = new User(
                "John",
                "Test",
                "other-history@test.com",
                "password123"
        );

        user1 = userRepository.save(user1);
        user2 = userRepository.save(user2);

        String user1Token =
                jwtService.generateAccessToken(user1.getEmail());

        String user2Token =
                jwtService.generateAccessToken(user2.getEmail());

        String walletBody = """
            {
              "userId": %d,
              "currency": "EUR"
            }
            """.formatted(user1.getId());

        String walletResponse = mockMvc.perform(
                        post("/api/wallets")
                                .header(
                                        "Authorization",
                                        "Bearer " + user1Token
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(walletBody)
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long walletId = objectMapper
                .readTree(walletResponse)
                .get("id")
                .asLong();

        mockMvc.perform(
                        get("/api/transactions/wallet/" + walletId)
                                .header(
                                        "Authorization",
                                        "Bearer " + user2Token
                                )
                                .param("page", "0")
                                .param("size", "10")
                )
                .andExpect(status().isForbidden());
    }
    @Test
    void createWalletShouldReturn403WhenCreatingForAnotherUser()
            throws Exception {

        User user1 = new User(
                "Razvan",
                "Test",
                "owner-create@test.com",
                "password123"
        );

        User user2 = new User(
                "John",
                "Test",
                "other-create@test.com",
                "password123"
        );

        user1 = userRepository.save(user1);
        user2 = userRepository.save(user2);

        String user1Token =
                jwtService.generateAccessToken(user1.getEmail());

        String requestBody = """
            {
              "userId": %d,
              "currency": "EUR"
            }
            """.formatted(user2.getId());

        mockMvc.perform(
                        post("/api/wallets")
                                .header(
                                        "Authorization",
                                        "Bearer " + user1Token
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isForbidden());
    }

    private Wallet createMoneyTestWallet(String email, String balance) {
        User user = userRepository.save(
                new User(
                        "Money",
                        "Test",
                        email,
                        "password123"
                )
        );

        return walletRepository.saveAndFlush(
                new Wallet(
                        Currency.EUR,
                        new BigDecimal(balance),
                        user
                )
        );
    }

    private void assertWalletBalance(Long walletId, String expected) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow();

        assertEquals(
                new BigDecimal(expected),
                wallet.getBalance()
        );
    }

    @Test
    void depositShouldRejectAmountWithThreeDecimals() throws Exception {
        Wallet wallet = createMoneyTestWallet(
                "decimal-deposit@test.com",
                "1.00"
        );

        String token =
                jwtService.generateAccessToken(wallet.getUser().getEmail());

        mockMvc.perform(
                        post("/api/wallets/{id}/deposit", wallet.getId())
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "amount": 0.015
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

        assertWalletBalance(wallet.getId(), "1.00");
        assertEquals(0L, transactionRepository.count());
    }

    @Test
    void transferShouldRejectAmountWithThreeDecimals() throws Exception {
        Wallet source = createMoneyTestWallet(
                "decimal-source@test.com",
                "1.00"
        );

        Wallet destination = createMoneyTestWallet(
                "decimal-destination@test.com",
                "0.00"
        );

        String token =
                jwtService.generateAccessToken(source.getUser().getEmail());

        String body = """
                {
                  "toWalletId": %d,
                  "amount": 0.015,
                  "description": "Invalid decimal transfer"
                }
                """.formatted(destination.getId());

        mockMvc.perform(
                        post("/api/wallets/{id}/transfer", source.getId())
                                .header("Authorization", "Bearer " + token)
                                .header(
                                        "Idempotency-Key",
                                        "reject-three-decimals"
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

        assertWalletBalance(source.getId(), "1.00");
        assertWalletBalance(destination.getId(), "0.00");
        assertEquals(0L, transactionRepository.count());
    }

    @Test
    void depositShouldRejectResultingBalanceOverflow() throws Exception {
        Wallet wallet = createMoneyTestWallet(
                "overflow-deposit@test.com",
                "99999999999999999.99"
        );

        String token =
                jwtService.generateAccessToken(wallet.getUser().getEmail());

        mockMvc.perform(
                        post("/api/wallets/{id}/deposit", wallet.getId())
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "amount": 0.01
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PARAMETER"));

        assertWalletBalance(wallet.getId(), "99999999999999999.99");
        assertEquals(0L, transactionRepository.count());
    }

    @Test
    void transferShouldRejectDestinationBalanceOverflow() throws Exception {
        Wallet source = createMoneyTestWallet(
                "overflow-source@test.com",
                "1.00"
        );

        Wallet destination = createMoneyTestWallet(
                "overflow-destination@test.com",
                "99999999999999999.99"
        );

        String token =
                jwtService.generateAccessToken(source.getUser().getEmail());

        String body = """
                {
                  "toWalletId": %d,
                  "amount": 0.01,
                  "description": "Destination overflow"
                }
                """.formatted(destination.getId());

        mockMvc.perform(
                        post("/api/wallets/{id}/transfer", source.getId())
                                .header("Authorization", "Bearer " + token)
                                .header(
                                        "Idempotency-Key",
                                        "reject-destination-overflow"
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PARAMETER"));

        assertWalletBalance(source.getId(), "1.00");
        assertWalletBalance(destination.getId(), "99999999999999999.99");
        assertEquals(0L, transactionRepository.count());
    }
}
