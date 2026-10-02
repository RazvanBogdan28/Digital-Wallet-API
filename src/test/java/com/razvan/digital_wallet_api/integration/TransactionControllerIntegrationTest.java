package com.razvan.digital_wallet_api.integration;

import com.razvan.digital_wallet_api.entity.Currency;
import com.razvan.digital_wallet_api.entity.Transaction;
import com.razvan.digital_wallet_api.entity.TransactionStatus;
import com.razvan.digital_wallet_api.entity.TransactionType;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

        TestWallet first = createWallet("history1@test.com");
        TestWallet second = createWallet("history2@test.com");

        deposit(first, "100.00");

        mockMvc.perform(
                        post("/api/wallets/{id}/transfer", first.wallet().getId())
                                .header(
                                        "Authorization",
                                        "Bearer " + first.token()
                                )
                                .header(
                                        "Idempotency-Key",
                                        UUID.randomUUID().toString()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "toWalletId": %d,
                                          "amount": "25.00",
                                          "description": "History integration test"
                                        }
                                        """.formatted(second.wallet().getId()))
                )
                .andExpect(status().isOk());

        mockMvc.perform(
                        historyRequest(first, 0, 10)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(
                        jsonPath("$.content[0].fromWalletId")
                                .value(first.wallet().getId().intValue())
                )
                .andExpect(
                        jsonPath("$.content[0].toWalletId")
                                .value(second.wallet().getId().intValue())
                )
                .andExpect(jsonPath("$.content[0].amount").isString())
                .andExpect(jsonPath("$.content[0].amount").value("25.00"))
                .andExpect(jsonPath("$.content[0].currency").value("EUR"))
                .andExpect(jsonPath("$.content[0].type").value("TRANSFER"))
                .andExpect(jsonPath("$.content[0].status").value("COMPLETED"))
                .andExpect(
                        jsonPath("$.content[0].description")
                                .value("History integration test")
                )
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(windowRequest(first, 100))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wallet.balance").isString())
                .andExpect(jsonPath("$.wallet.balance").value("75.00"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].type").value("TRANSFER"))
                .andExpect(jsonPath("$.items[0].amount").value("25.00"))
                .andExpect(jsonPath("$.items[1].type").value("DEPOSIT"))
                .andExpect(jsonPath("$.items[1].amount").value("100.00"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.complete").value(true));

        mockMvc.perform(windowRequest(second, 100))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wallet.balance").value("25.00"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].type").value("TRANSFER"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.complete").value(true));
    }

    @Test
    void getTransactionsByWalletIdShouldReturn404WhenWalletNotFound()
            throws Exception {

        TestWallet fixture = createWallet("notfound@test.com");

        mockMvc.perform(
                        get("/api/transactions/wallet/{id}", Long.MAX_VALUE)
                                .header(
                                        "Authorization",
                                        "Bearer " + fixture.token()
                                )
                                .param("page", "0")
                                .param("size", "10")
                )
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.error").value("WALLET_NOT_FOUND")
                );
    }

    @Test
    void recentWindowShouldReturnEmptyHistoryAndZeroBalance()
            throws Exception {

        TestWallet fixture = createWallet("empty-window@test.com");

        String response = mockMvc.perform(windowRequest(fixture, 100))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.wallet.id")
                                .value(fixture.wallet().getId().intValue())
                )
                .andExpect(
                        jsonPath("$.wallet.userId")
                                .value(fixture.user().getId().intValue())
                )
                .andExpect(jsonPath("$.wallet.currency").value("EUR"))
                .andExpect(jsonPath("$.wallet.balance").isString())
                .andExpect(jsonPath("$.wallet.balance").value("0.00"))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.complete").value(true))
                .andExpect(jsonPath("$.snapshotAt").isString())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Instant snapshotAt = Instant.parse(
                objectMapper.readTree(response)
                        .get("snapshotAt")
                        .asText()
        );

        assertTrue(!snapshotAt.isAfter(Instant.now()));
    }

    @Test
    void historyAndWindowShouldOrderEqualTimestampsByDescendingId()
            throws Exception {

        TestWallet fixture = createWallet("stable-order@test.com");
        Instant sameTime = Instant.parse("2026-01-01T10:00:00Z");

        Transaction first = saveDepositAt(fixture.wallet(), "1.00", sameTime);
        Transaction second = saveDepositAt(fixture.wallet(), "2.00", sameTime);
        Transaction third = saveDepositAt(fixture.wallet(), "3.00", sameTime);

        Wallet wallet = walletRepository.findById(fixture.wallet().getId())
                .orElseThrow();

        wallet.setBalance(new BigDecimal("6.00"));
        walletRepository.saveAndFlush(wallet);

        mockMvc.perform(windowRequest(fixture, 100))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wallet.balance").value("6.00"))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(
                        jsonPath("$.items[0].id")
                                .value(third.getId().intValue())
                )
                .andExpect(
                        jsonPath("$.items[1].id")
                                .value(second.getId().intValue())
                )
                .andExpect(
                        jsonPath("$.items[2].id")
                                .value(first.getId().intValue())
                )
                .andExpect(jsonPath("$.complete").value(true));

        mockMvc.perform(historyRequest(fixture, 0, 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(
                        jsonPath("$.content[0].id")
                                .value(third.getId().intValue())
                )
                .andExpect(
                        jsonPath("$.content[1].id")
                                .value(second.getId().intValue())
                )
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(historyRequest(fixture, 1, 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(
                        jsonPath("$.content[0].id")
                                .value(first.getId().intValue())
                );
    }

    @Test
    void recentWindowShouldLimitItemsAndReportIncompleteHistory()
            throws Exception {

        TestWallet fixture = createWallet("limited-window@test.com");

        deposit(fixture, "1.00");
        deposit(fixture, "2.00");
        deposit(fixture, "3.00");

        mockMvc.perform(windowRequest(fixture, 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wallet.balance").value("6.00"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].amount").value("3.00"))
                .andExpect(jsonPath("$.items[1].amount").value("2.00"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.complete").value(false));

        mockMvc.perform(windowRequest(fixture, 3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.complete").value(true));
    }

    @Test
    void recentWindowShouldRejectAnotherUsersWallet()
            throws Exception {

        TestWallet requester = createWallet("window-requester@test.com");
        TestWallet owner = createWallet("window-owner@test.com");

        deposit(owner, "10.00");

        mockMvc.perform(
                        get(
                                "/api/transactions/wallet/{id}/window",
                                owner.wallet().getId()
                        )
                                .header(
                                        "Authorization",
                                        "Bearer " + requester.token()
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.wallet").doesNotExist())
                .andExpect(jsonPath("$.items").doesNotExist());
    }

    @Test
    void recentWindowShouldRequireAuthentication()
            throws Exception {

        TestWallet fixture = createWallet("window-no-auth@test.com");

        mockMvc.perform(
                        get(
                                "/api/transactions/wallet/{id}/window",
                                fixture.wallet().getId()
                        )
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.wallet").doesNotExist())
                .andExpect(jsonPath("$.items").doesNotExist());
    }

    @Test
    void recentWindowShouldReturn404ForMissingWallet()
            throws Exception {

        TestWallet fixture = createWallet("window-notfound@test.com");

        mockMvc.perform(
                        get(
                                "/api/transactions/wallet/{id}/window",
                                Long.MAX_VALUE
                        )
                                .header(
                                        "Authorization",
                                        "Bearer " + fixture.token()
                                )
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("WALLET_NOT_FOUND"));
    }

    @Test
    void recentWindowShouldRejectInvalidSizes()
            throws Exception {

        TestWallet fixture = createWallet("window-invalid-size@test.com");

        for (int size : new int[]{-1, 0, 101}) {
            mockMvc.perform(windowRequest(fixture, size))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("INVALID_PARAMETER"))
                    .andExpect(
                            jsonPath("$.message")
                                    .value("Size must be between 1 and 100")
                    );
        }
    }

    @Test
    void recentWindowShouldKeepBalanceAndHistoryConsistentDuringDeposits()
            throws Exception {

        TestWallet fixture = createWallet("window-concurrent@test.com");

        var executor = Executors.newSingleThreadExecutor();
        CountDownLatch ready = new CountDownLatch(1);
        CountDownLatch start = new CountDownLatch(1);

        try {
            var writer = executor.submit(() -> {
                ready.countDown();

                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Start timeout");
                }

                for (int i = 0; i < 20; i++) {
                    deposit(fixture, "1.00");
                }

                return true;
            });

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            for (int i = 0; i < 20; i++) {
                String response = mockMvc.perform(windowRequest(fixture, 100))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.complete").value(true))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

                var root = objectMapper.readTree(response);

                BigDecimal balance = new BigDecimal(
                        root.get("wallet").get("balance").asText()
                );

                BigDecimal sum = BigDecimal.ZERO;

                for (var transaction : root.get("items")) {
                    sum = sum.add(
                            new BigDecimal(transaction.get("amount").asText())
                    );

                    assertEquals(
                            "DEPOSIT",
                            transaction.get("type").asText()
                    );
                }

                assertEquals(
                        0,
                        balance.compareTo(sum),
                        "Snapshot balance must match its deposit history"
                );

                assertEquals(
                        (long) root.get("items").size(),
                        root.get("totalElements").asLong()
                );
            }

            assertTrue(writer.get(30, TimeUnit.SECONDS));

            mockMvc.perform(windowRequest(fixture, 100))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.wallet.balance").value("20.00"))
                    .andExpect(jsonPath("$.items.length()").value(20))
                    .andExpect(jsonPath("$.totalElements").value(20))
                    .andExpect(jsonPath("$.complete").value(true));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private TestWallet createWallet(String email) throws Exception {
        User user = userRepository.save(
                new User(
                        "Razvan",
                        "Test",
                        email,
                        "password123"
                )
        );

        String token = jwtService.generateAccessToken(user.getEmail());

        String response = mockMvc.perform(
                        post("/api/wallets")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId": %d,
                                          "currency": "EUR"
                                        }
                                        """.formatted(user.getId()))
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long walletId = objectMapper.readTree(response)
                .get("id")
                .asLong();

        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow();

        return new TestWallet(user, wallet, token);
    }

    private void deposit(TestWallet fixture, String amount)
            throws Exception {

        mockMvc.perform(
                        post("/api/wallets/{id}/deposit", fixture.wallet().getId())
                                .header(
                                        "Authorization",
                                        "Bearer " + fixture.token()
                                )
                                .header(
                                        "Idempotency-Key",
                                        UUID.randomUUID().toString()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "amount": "%s"
                                        }
                                        """.formatted(amount))
                )
                .andExpect(status().isOk());
    }

    private Transaction saveDepositAt(
            Wallet wallet,
            String amount,
            Instant createdAt
    ) {
        return transactionRepository.saveAndFlush(
                new Transaction(
                        wallet,
                        wallet,
                        new BigDecimal(amount),
                        wallet.getCurrency(),
                        TransactionType.DEPOSIT,
                        TransactionStatus.COMPLETED,
                        createdAt,
                        UUID.randomUUID().toString(),
                        "Deposit"
                )
        );
    }

    private MockHttpServletRequestBuilder historyRequest(
            TestWallet fixture,
            int page,
            int size
    ) {
        return get(
                "/api/transactions/wallet/{id}",
                fixture.wallet().getId()
        )
                .header("Authorization", "Bearer " + fixture.token())
                .param("page", String.valueOf(page))
                .param("size", String.valueOf(size));
    }

    private MockHttpServletRequestBuilder windowRequest(
            TestWallet fixture,
            int size
    ) {
        return get(
                "/api/transactions/wallet/{id}/window",
                fixture.wallet().getId()
        )
                .header("Authorization", "Bearer " + fixture.token())
                .param("size", String.valueOf(size));
    }

    private record TestWallet(User user, Wallet wallet, String token) {
    }
}