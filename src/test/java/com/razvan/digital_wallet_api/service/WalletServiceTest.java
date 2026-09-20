package com.razvan.digital_wallet_api.service;

import com.razvan.digital_wallet_api.dto.CreateWalletRequest;
import com.razvan.digital_wallet_api.dto.DepositRequest;
import com.razvan.digital_wallet_api.dto.TransferRequest;
import com.razvan.digital_wallet_api.dto.WalletResponse;
import com.razvan.digital_wallet_api.entity.Currency;
import com.razvan.digital_wallet_api.entity.Transaction;
import com.razvan.digital_wallet_api.entity.User;
import com.razvan.digital_wallet_api.entity.Wallet;
import com.razvan.digital_wallet_api.exception.CurrencyMismatchException;
import com.razvan.digital_wallet_api.exception.DuplicateTransactionException;
import com.razvan.digital_wallet_api.exception.InsufficientFundsException;
import com.razvan.digital_wallet_api.exception.SameWalletTransferException;
import com.razvan.digital_wallet_api.exception.UserNotFoundException;
import com.razvan.digital_wallet_api.exception.WalletAlreadyExistsException;
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

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private WalletService walletService;

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

    private void mockAuthenticatedUser() {
        when(userRepository.findByEmail("razvan@test.com"))
                .thenReturn(Optional.of(user1));
    }

    @Test
    void transferShouldMoveMoneyBetweenWallets() {

        TransferRequest request = new TransferRequest();
        request.setToWalletId(2L);
        request.setAmount(new BigDecimal("25.00"));
        request.setDescription("Test transfer");

        when(transactionRepository
                .findByIdempotencyKey("test-key"))
                .thenReturn(Optional.empty());

        when(walletRepository.findById(1L))
                .thenReturn(Optional.of(fromWallet));

        when(walletRepository.findById(2L))
                .thenReturn(Optional.of(toWallet));

        when(walletRepository.save(any(Wallet.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockAuthenticatedUser();

        WalletResponse response = walletService.transfer(
                1L,
                request,
                "test-key"
        );

        assertEquals(
                new BigDecimal("75.00"),
                fromWallet.getBalance()
        );

        assertEquals(
                new BigDecimal("75.00"),
                toWallet.getBalance()
        );

        assertEquals(
                new BigDecimal("75.00"),
                response.getBalance()
        );
    }

    @Test
    void transferShouldThrowExceptionWhenInsufficientFunds() {

        TransferRequest request = new TransferRequest();
        request.setToWalletId(2L);
        request.setAmount(new BigDecimal("150.00"));
        request.setDescription("Too much");

        when(transactionRepository
                .findByIdempotencyKey("test-key"))
                .thenReturn(Optional.empty());

        when(walletRepository.findById(1L))
                .thenReturn(Optional.of(fromWallet));

        when(walletRepository.findById(2L))
                .thenReturn(Optional.of(toWallet));

        mockAuthenticatedUser();

        assertThrows(
                InsufficientFundsException.class,
                () -> walletService.transfer(
                        1L,
                        request,
                        "test-key"
                )
        );
    }

    @Test
    void transferShouldThrowExceptionWhenSameWallet() {

        TransferRequest request = new TransferRequest();
        request.setToWalletId(1L);
        request.setAmount(new BigDecimal("10.00"));

        when(transactionRepository
                .findByIdempotencyKey("test-key"))
                .thenReturn(Optional.empty());

        when(walletRepository.findById(1L))
                .thenReturn(Optional.of(fromWallet));

        mockAuthenticatedUser();

        assertThrows(
                SameWalletTransferException.class,
                () -> walletService.transfer(
                        1L,
                        request,
                        "test-key"
                )
        );
    }

    @Test
    void transferShouldThrowExceptionWhenCurrenciesMismatch() {

        TransferRequest request = new TransferRequest();
        request.setToWalletId(2L);
        request.setAmount(new BigDecimal("10.00"));

        toWallet.setCurrency(Currency.USD);

        when(transactionRepository
                .findByIdempotencyKey("test-key"))
                .thenReturn(Optional.empty());

        when(walletRepository.findById(1L))
                .thenReturn(Optional.of(fromWallet));

        when(walletRepository.findById(2L))
                .thenReturn(Optional.of(toWallet));

        mockAuthenticatedUser();

        assertThrows(
                CurrencyMismatchException.class,
                () -> walletService.transfer(
                        1L,
                        request,
                        "test-key"
                )
        );
    }

    @Test
    void transferShouldThrowExceptionWhenIdempotencyKeyAlreadyExists() {

        TransferRequest request = new TransferRequest();
        request.setToWalletId(2L);
        request.setAmount(new BigDecimal("10.00"));

        when(transactionRepository
                .findByIdempotencyKey("duplicate-key"))
                .thenReturn(Optional.of(
                        org.mockito.Mockito.mock(Transaction.class)
                ));

        assertThrows(
                DuplicateTransactionException.class,
                () -> walletService.transfer(
                        1L,
                        request,
                        "duplicate-key"
                )
        );
    }

    @Test
    void transferShouldSaveTransaction() {

        TransferRequest request = new TransferRequest();
        request.setToWalletId(2L);
        request.setAmount(new BigDecimal("25.00"));
        request.setDescription("Test transaction");

        when(transactionRepository
                .findByIdempotencyKey("save-test-key"))
                .thenReturn(Optional.empty());

        when(walletRepository.findById(1L))
                .thenReturn(Optional.of(fromWallet));

        when(walletRepository.findById(2L))
                .thenReturn(Optional.of(toWallet));

        when(walletRepository.save(any(Wallet.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockAuthenticatedUser();

        walletService.transfer(
                1L,
                request,
                "save-test-key"
        );

        verify(transactionRepository)
                .save(any());
    }

    @Test
    void depositShouldIncreaseWalletBalance() {

        DepositRequest request = new DepositRequest();
        request.setAmount(new BigDecimal("25.00"));

        when(walletRepository.findById(1L))
                .thenReturn(Optional.of(fromWallet));

        when(walletRepository.save(any(Wallet.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockAuthenticatedUser();

        WalletResponse response = walletService.deposit(
                1L,
                request
        );

        assertEquals(
                new BigDecimal("125.00"),
                fromWallet.getBalance()
        );

        assertEquals(
                new BigDecimal("125.00"),
                response.getBalance()
        );

        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    void depositShouldThrowExceptionWhenWalletNotFound() {

        DepositRequest request = new DepositRequest();
        request.setAmount(new BigDecimal("25.00"));

        when(walletRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                WalletNotFoundException.class,
                () -> walletService.deposit(
                        999L,
                        request
                )
        );
    }

    @Test
    void createWalletShouldCreateWalletSuccessfully() {

        CreateWalletRequest request = new CreateWalletRequest();
        request.setUserId(1L);
        request.setCurrency(Currency.EUR);

        mockAuthenticatedUser();

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user1));

        when(walletRepository
                .existsByUserIdAndCurrency(1L, Currency.EUR))
                .thenReturn(false);

        when(walletRepository.save(any(Wallet.class)))
                .thenAnswer(invocation -> {
                    Wallet wallet = invocation.getArgument(0);
                    ReflectionTestUtils.setField(wallet, "id", 10L);
                    return wallet;
                });

        WalletResponse response =
                walletService.createWallet(request);

        assertEquals(10L, response.getId());
        assertEquals(1L, response.getUserId());
        assertEquals(Currency.EUR, response.getCurrency());
        assertEquals(BigDecimal.ZERO, response.getBalance());
    }

    @Test
    void createWalletShouldThrowExceptionWhenWalletAlreadyExists() {

        CreateWalletRequest request = new CreateWalletRequest();
        request.setUserId(1L);
        request.setCurrency(Currency.EUR);

        mockAuthenticatedUser();

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user1));

        when(walletRepository
                .existsByUserIdAndCurrency(1L, Currency.EUR))
                .thenReturn(true);

        assertThrows(
                WalletAlreadyExistsException.class,
                () -> walletService.createWallet(request)
        );
    }

    @Test
    void createWalletShouldThrowExceptionWhenUserNotFound() {

        CreateWalletRequest request = new CreateWalletRequest();
        request.setUserId(999L);
        request.setCurrency(Currency.EUR);

        ReflectionTestUtils.setField(user1, "id", 999L);

        mockAuthenticatedUser();

        when(userRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                UserNotFoundException.class,
                () -> walletService.createWallet(request)
        );
    }

    @Test
    void createWalletShouldThrowExceptionWhenCreatingForAnotherUser() {

        CreateWalletRequest request = new CreateWalletRequest();
        request.setUserId(2L);
        request.setCurrency(Currency.EUR);

        mockAuthenticatedUser();

        assertThrows(
                AccessDeniedException.class,
                () -> walletService.createWallet(request)
        );
    }

}