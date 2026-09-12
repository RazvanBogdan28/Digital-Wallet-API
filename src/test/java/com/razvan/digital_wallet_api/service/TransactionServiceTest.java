package com.razvan.digital_wallet_api.service;

import com.razvan.digital_wallet_api.dto.TransactionResponse;
import com.razvan.digital_wallet_api.entity.Currency;
import com.razvan.digital_wallet_api.entity.Transaction;
import com.razvan.digital_wallet_api.entity.TransactionStatus;
import com.razvan.digital_wallet_api.entity.TransactionType;
import com.razvan.digital_wallet_api.entity.User;
import com.razvan.digital_wallet_api.entity.Wallet;
import com.razvan.digital_wallet_api.exception.WalletNotFoundException;
import com.razvan.digital_wallet_api.repository.TransactionRepository;
import com.razvan.digital_wallet_api.repository.UserRepository;
import com.razvan.digital_wallet_api.repository.WalletRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TransactionService transactionService;

    private User user1;
    private User user2;

    private Wallet fromWallet;
    private Wallet toWallet;

    @BeforeEach
    void setUp() {

        user1 = new User(
                "Razvan",
                "Test",
                "razvan@test.com",
                "hashedPassword"
        );

        user2 = new User(
                "John",
                "Test",
                "john@test.com",
                "hashedPassword"
        );

        ReflectionTestUtils.setField(user1, "id", 1L);
        ReflectionTestUtils.setField(user2, "id", 2L);

        fromWallet = new Wallet(
                Currency.EUR,
                new BigDecimal("100.00"),
                user1
        );

        toWallet = new Wallet(
                Currency.EUR,
                new BigDecimal("50.00"),
                user2
        );

        ReflectionTestUtils.setField(fromWallet, "id", 1L);
        ReflectionTestUtils.setField(toWallet, "id", 2L);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        "razvan@test.com",
                        null,
                        Collections.emptyList()
                );

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getTransactionsByWalletIdShouldReturnTransactions() {

        Transaction transaction = new Transaction(
                fromWallet,
                toWallet,
                new BigDecimal("25.00"),
                Currency.EUR,
                TransactionType.TRANSFER,
                TransactionStatus.COMPLETED,
                LocalDateTime.now(),
                "test-key",
                "Test transfer"
        );

        ReflectionTestUtils.setField(transaction, "id", 100L);

        Page<Transaction> transactionPage =
                new PageImpl<>(List.of(transaction));

        when(walletRepository.findById(1L))
                .thenReturn(Optional.of(fromWallet));

        when(userRepository.findByEmail("razvan@test.com"))
                .thenReturn(Optional.of(user1));

        when(transactionRepository
                .findByFromWalletIdOrToWalletIdOrderByCreatedAtDesc(
                        any(),
                        any(),
                        any(Pageable.class)
                ))
                .thenReturn(transactionPage);

        Page<TransactionResponse> result =
                transactionService.getTransactionsByWalletId(
                        1L,
                        0,
                        10
                );

        assertEquals(1, result.getTotalElements());
        assertEquals(100L, result.getContent().get(0).getId());
        assertEquals(
                new BigDecimal("25.00"),
                result.getContent().get(0).getAmount()
        );
        assertEquals(
                "Test transfer",
                result.getContent().get(0).getDescription()
        );
    }

    @Test
    void getTransactionsByWalletIdShouldThrowExceptionWhenWalletNotFound() {

        when(walletRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                WalletNotFoundException.class,
                () -> transactionService.getTransactionsByWalletId(
                        999L,
                        0,
                        10
                )
        );
    }
}