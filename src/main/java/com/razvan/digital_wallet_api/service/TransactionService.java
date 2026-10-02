package com.razvan.digital_wallet_api.service;

import com.razvan.digital_wallet_api.dto.TransactionResponse;
import com.razvan.digital_wallet_api.dto.WalletResponse;
import com.razvan.digital_wallet_api.dto.WalletTransactionWindowResponse;
import com.razvan.digital_wallet_api.entity.Transaction;
import com.razvan.digital_wallet_api.entity.User;
import com.razvan.digital_wallet_api.entity.Wallet;
import com.razvan.digital_wallet_api.exception.UserNotFoundException;
import com.razvan.digital_wallet_api.exception.WalletNotFoundException;
import com.razvan.digital_wallet_api.repository.TransactionRepository;
import com.razvan.digital_wallet_api.repository.UserRepository;
import com.razvan.digital_wallet_api.repository.WalletRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final UserRepository userRepository;

    public TransactionService(
            TransactionRepository transactionRepository,
            WalletRepository walletRepository,
            UserRepository userRepository
    ) {
        this.transactionRepository = transactionRepository;
        this.walletRepository = walletRepository;
        this.userRepository = userRepository;
    }

    @Transactional(
            readOnly = true,
            isolation = Isolation.REPEATABLE_READ
    )
    public Page<TransactionResponse> getTransactionsByWalletId(
            Long walletId,
            int page,
            int size
    ) {
        validatePagination(page, size);
        getOwnedWallet(walletId);

        return transactionRepository
                .findByFromWalletIdOrToWalletIdOrderByCreatedAtDesc(
                        walletId,
                        walletId,
                        PageRequest.of(page, size)
                )
                .map(this::mapToResponse);
    }

    @Transactional(
            readOnly = true,
            isolation = Isolation.REPEATABLE_READ
    )
    public WalletTransactionWindowResponse getRecentWindow(
            Long walletId,
            int size
    ) {
        validatePagination(0, size);

        Instant snapshotAt = Instant.now();

        Wallet wallet = getOwnedWallet(walletId);

        Page<TransactionResponse> transactions =
                transactionRepository
                        .findByFromWalletIdOrToWalletIdOrderByCreatedAtDesc(
                                walletId,
                                walletId,
                                PageRequest.of(0, size)
                        )
                        .map(this::mapToResponse);

        WalletResponse walletResponse = new WalletResponse(
                wallet.getId(),
                wallet.getUser().getId(),
                wallet.getCurrency(),
                wallet.getBalance()
        );

        return new WalletTransactionWindowResponse(
                walletResponse,
                transactions.getContent(),
                transactions.getTotalElements(),
                snapshotAt
        );
    }

    private void validatePagination(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException(
                    "Page cannot be negative"
            );
        }

        if (size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "Size must be between 1 and 100"
            );
        }
    }

    private Wallet getOwnedWallet(Long walletId) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException(
                        "Wallet not found with id: " + walletId
                ));

        verifyWalletOwnership(wallet);

        return wallet;
    }

    private TransactionResponse mapToResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getFromWallet().getId(),
                transaction.getToWallet().getId(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getType().name(),
                transaction.getStatus().name(),
                transaction.getCreatedAt(),
                transaction.getDescription()
        );
    }

    private User getAuthenticatedUser() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException(
                    "Authentication required"
            );
        }

        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UserNotFoundException(
                        "Authenticated user not found"
                ));
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