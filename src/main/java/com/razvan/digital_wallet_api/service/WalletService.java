package com.razvan.digital_wallet_api.service;

import com.razvan.digital_wallet_api.dto.CreateWalletRequest;
import com.razvan.digital_wallet_api.dto.WalletResponse;
import com.razvan.digital_wallet_api.entity.User;
import com.razvan.digital_wallet_api.entity.Wallet;
import com.razvan.digital_wallet_api.exception.UserNotFoundException;
import com.razvan.digital_wallet_api.repository.UserRepository;
import com.razvan.digital_wallet_api.repository.WalletRepository;
import org.springframework.stereotype.Service;
import com.razvan.digital_wallet_api.exception.WalletAlreadyExistsException;
import com.razvan.digital_wallet_api.exception.WalletNotFoundException;
import com.razvan.digital_wallet_api.dto.DepositRequest;
import com.razvan.digital_wallet_api.dto.TransferRequest;
import com.razvan.digital_wallet_api.exception.InsufficientFundsException;
import org.springframework.transaction.annotation.Transactional;
import com.razvan.digital_wallet_api.entity.Transaction;
import com.razvan.digital_wallet_api.entity.TransactionStatus;
import com.razvan.digital_wallet_api.entity.TransactionType;
import com.razvan.digital_wallet_api.repository.TransactionRepository;
import com.razvan.digital_wallet_api.exception.SameWalletTransferException;
import com.razvan.digital_wallet_api.exception.CurrencyMismatchException;
import com.razvan.digital_wallet_api.exception.DuplicateTransactionException;
import com.razvan.digital_wallet_api.entity.Currency;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import com.razvan.digital_wallet_api.entity.TransactionStatus;
import com.razvan.digital_wallet_api.entity.TransactionType;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
@Service
public class WalletService {

    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    public WalletService(
            WalletRepository walletRepository,
            UserRepository userRepository,
            TransactionRepository transactionRepository) {

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
                currency)) {

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

    private WalletResponse mapToResponse(Wallet wallet) {
        return new WalletResponse(
                wallet.getId(),
                wallet.getUser().getId(),
                wallet.getCurrency(),
                wallet.getBalance()
        );
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
    public WalletResponse deposit(Long walletId, DepositRequest request) {

        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() ->
                        new WalletNotFoundException(
                                "Wallet not found with id: " + walletId
                        )
                );

        verifyWalletOwnership(wallet);

        BigDecimal newBalance = wallet.getBalance()
                .add(request.getAmount());

        wallet.setBalance(newBalance);

        Wallet savedWallet = walletRepository.save(wallet);

        Transaction transaction = new Transaction(
                wallet,
                wallet,
                request.getAmount(),
                wallet.getCurrency(),
                TransactionType.DEPOSIT,
                TransactionStatus.COMPLETED,
                LocalDateTime.now(),
                UUID.randomUUID().toString(),
                "Deposit"
        );

        transactionRepository.save(transaction);

        return mapToResponse(savedWallet);
    }

    @Transactional
    public WalletResponse transfer(
            Long fromWalletId,
            TransferRequest request,
            String idempotencyKey) {

        if (transactionRepository
                .findByIdempotencyKey(idempotencyKey)
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
                                "Destination wallet not found with id: " + request.getToWalletId()
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

        if (fromWallet.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient funds in wallet with id: " + fromWalletId
            );
        }

        fromWallet.setBalance(
                fromWallet.getBalance().subtract(request.getAmount())
        );

        toWallet.setBalance(
                toWallet.getBalance().add(request.getAmount())
        );

        walletRepository.save(fromWallet);
        walletRepository.save(toWallet);

        Transaction transaction = new Transaction(
                fromWallet,
                toWallet,
                request.getAmount(),
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