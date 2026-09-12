package com.razvan.digital_wallet_api.service;

import com.razvan.digital_wallet_api.dto.TransactionResponse;
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
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

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

    public Page<TransactionResponse> getTransactionsByWalletId(
            Long walletId,
            int page,
            int size
    ) {

        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() ->
                        new WalletNotFoundException(
                                "Wallet not found with id: " + walletId
                        )
                );

        verifyWalletOwnership(wallet);

        Pageable pageable = PageRequest.of(page, size);

        return transactionRepository
                .findByFromWalletIdOrToWalletIdOrderByCreatedAtDesc(
                        walletId,
                        walletId,
                        pageable
                )
                .map(this::mapToResponse);
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

        String email = authentication.getName();

        return userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new UserNotFoundException(
                                "Authenticated user not found"
                        )
                );
    }

    private void verifyWalletOwnership(Wallet wallet) {

        User authenticatedUser = getAuthenticatedUser();

        if (!wallet.getUser().getId()
                .equals(authenticatedUser.getId())) {

            throw new AccessDeniedException(
                    "You do not have access to this wallet"
            );
        }
    }
}