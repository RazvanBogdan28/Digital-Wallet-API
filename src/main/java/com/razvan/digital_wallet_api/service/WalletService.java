package com.razvan.digital_wallet_api.service;

import com.razvan.digital_wallet_api.dto.CreateWalletRequest;
import com.razvan.digital_wallet_api.dto.DepositRequest;
import com.razvan.digital_wallet_api.dto.TransferRequest;
import com.razvan.digital_wallet_api.dto.WalletResponse;
import com.razvan.digital_wallet_api.entity.Currency;
import com.razvan.digital_wallet_api.entity.Transaction;
import com.razvan.digital_wallet_api.entity.TransactionStatus;
import com.razvan.digital_wallet_api.entity.TransactionType;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class WalletService {

    private static final BigDecimal MIN_AMOUNT =
            new BigDecimal("0.01");

    private static final BigDecimal MAX_BALANCE =
            new BigDecimal("99999999999999999.99");

    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    public WalletService(
            WalletRepository walletRepository,
            UserRepository userRepository,
            TransactionRepository transactionRepository
    ) {
        this.walletRepository = walletRepository;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
    }

    public WalletResponse createWallet(CreateWalletRequest request) {
        User authenticatedUser = getAuthenticatedUser();

        if (!authenticatedUser.getId().equals(request.getUserId())) {
            throw new AccessDeniedException(
                    "You cannot create a wallet for another user"
            );
        }

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() ->
                        new UserNotFoundException(
                                "User not found with id: " + request.getUserId()
                        )
                );

        Currency currency = request.getCurrency();

        if (walletRepository.existsByUserIdAndCurrency(
                request.getUserId(),
                currency
        )) {
            throw new WalletAlreadyExistsException(
                    "User already has a wallet in currency: " + currency
            );
        }

        Wallet wallet = new Wallet(
                currency,
                BigDecimal.ZERO,
                user
        );

        Wallet savedWallet = walletRepository.save(wallet);

        return mapToResponse(savedWallet);
    }

    public List<WalletResponse> getWalletsByUserId(Long userId) {
        User authenticatedUser = getAuthenticatedUser();

        if (!authenticatedUser.getId().equals(userId)) {
            throw new AccessDeniedException(
                    "You do not have access to these wallets"
            );
        }

        return walletRepository.findByUserId(userId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public WalletResponse getWalletById(Long id) {
        Wallet wallet = walletRepository.findById(id)
                .orElseThrow(() ->
                        new WalletNotFoundException(
                                "Wallet not found with id: " + id
                        )
                );

        verifyWalletOwnership(wallet);

        return mapToResponse(wallet);
    }

    @Transactional
    public WalletResponse deposit(
            Long walletId,
            DepositRequest request,
            String idempotencyKey
    ) {
        if (idempotencyKey == null
                || idempotencyKey.isBlank()
                || idempotencyKey.length() > 255) {
            throw new IllegalArgumentException(
                    "Idempotency-Key must contain between 1 and 255 characters"
            );
        }

        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() ->
                        new WalletNotFoundException(
                                "Wallet not found with id: " + walletId
                        )
                );

        verifyWalletOwnership(wallet);

        BigDecimal amount = validateAmount(request.getAmount());

        var existing =
                transactionRepository.findByIdempotencyKey(idempotencyKey);

        if (existing.isPresent()) {
            Transaction previous = existing.get();

            boolean sameDeposit =
                    previous.getType() == TransactionType.DEPOSIT
                            && previous.getStatus() == TransactionStatus.COMPLETED
                            && previous.getFromWallet().getId().equals(walletId)
                            && previous.getToWallet().getId().equals(walletId)
                            && previous.getCurrency() == wallet.getCurrency()
                            && previous.getAmount().compareTo(amount) == 0;

            if (!sameDeposit) {
                throw new IllegalArgumentException(
                        "Idempotency-Key was already used for a different request"
                );
            }

            throw new DuplicateTransactionException(
                    "Deposit already processed"
            );
        }

        BigDecimal newBalance = wallet.getBalance().add(amount);

        validateBalance(newBalance);

        Transaction transaction = new Transaction(
                wallet,
                wallet,
                amount,
                wallet.getCurrency(),
                TransactionType.DEPOSIT,
                TransactionStatus.COMPLETED,
                LocalDateTime.now(),
                idempotencyKey,
                "Deposit"
        );

        transactionRepository.saveAndFlush(transaction);

        wallet.setBalance(newBalance);

        Wallet savedWallet = walletRepository.save(wallet);

        return mapToResponse(savedWallet);
    }

    @Transactional
    public WalletResponse transfer(
            Long fromWalletId,
            TransferRequest request,
            String idempotencyKey
    ) {
        if (transactionRepository.findByIdempotencyKey(idempotencyKey)
                .isPresent()) {
            throw new DuplicateTransactionException(
                    "Transfer already processed"
            );
        }

        Wallet fromWallet = walletRepository.findById(fromWalletId)
                .orElseThrow(() ->
                        new WalletNotFoundException(
                                "Source wallet not found with id: " + fromWalletId
                        )
                );

        verifyWalletOwnership(fromWallet);

        Wallet toWallet = walletRepository.findById(request.getToWalletId())
                .orElseThrow(() ->
                        new WalletNotFoundException(
                                "Destination wallet not found with id: "
                                        + request.getToWalletId()
                        )
                );

        if (fromWallet.getId().equals(toWallet.getId())) {
            throw new SameWalletTransferException(
                    "Source and destination wallet cannot be the same"
            );
        }

        if (!fromWallet.getCurrency().equals(toWallet.getCurrency())) {
            throw new CurrencyMismatchException(
                    "Wallet currencies must match"
            );
        }

        BigDecimal amount = validateAmount(request.getAmount());

        if (fromWallet.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient funds in wallet with id: " + fromWalletId
            );
        }

        BigDecimal newFromBalance =
                fromWallet.getBalance().subtract(amount);

        BigDecimal newToBalance =
                toWallet.getBalance().add(amount);

        validateBalance(newFromBalance);
        validateBalance(newToBalance);

        fromWallet.setBalance(newFromBalance);
        toWallet.setBalance(newToBalance);

        walletRepository.save(fromWallet);
        walletRepository.save(toWallet);

        Transaction transaction = new Transaction(
                fromWallet,
                toWallet,
                amount,
                fromWallet.getCurrency(),
                TransactionType.TRANSFER,
                TransactionStatus.COMPLETED,
                LocalDateTime.now(),
                idempotencyKey,
                request.getDescription()
        );

        transactionRepository.save(transaction);

        return mapToResponse(fromWallet);
    }

    private BigDecimal validateAmount(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException(
                    "Amount is required"
            );
        }

        if (amount.compareTo(MIN_AMOUNT) < 0) {
            throw new IllegalArgumentException(
                    "Amount must be at least 0.01"
            );
        }

        if (amount.compareTo(MAX_BALANCE) > 0) {
            throw new IllegalArgumentException(
                    "Amount exceeds the supported limit"
            );
        }

        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException(
                    "Amount must not require rounding to two decimal places"
            );
        }
    }

    private void validateBalance(BigDecimal balance) {
        if (balance.signum() < 0) {
            throw new IllegalArgumentException(
                    "Wallet balance cannot be negative"
            );
        }

        if (balance.compareTo(MAX_BALANCE) > 0) {
            throw new IllegalArgumentException(
                    "Resulting wallet balance exceeds the supported limit"
            );
        }
    }

    private WalletResponse mapToResponse(Wallet wallet) {
        return new WalletResponse(
                wallet.getId(),
                wallet.getUser().getId(),
                wallet.getCurrency(),
                wallet.getBalance()
        );
    }

    private User getAuthenticatedUser() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()) {
            throw new AccessDeniedException(
                    "Authentication required"
            );
        }

        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() ->
                        new UserNotFoundException(
                                "Authenticated user not found"
                        )
                );
    }

    private void verifyWalletOwnership(Wallet wallet) {
        User authenticatedUser = getAuthenticatedUser();

        if (!wallet.getUser().getId().equals(authenticatedUser.getId())) {
            throw new AccessDeniedException(
                    "You do not have access to this wallet"
            );
        }
    }
}