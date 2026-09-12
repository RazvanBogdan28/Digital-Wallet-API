package com.razvan.digital_wallet_api.repository;

import com.razvan.digital_wallet_api.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Page<Transaction> findByFromWalletIdOrToWalletIdOrderByCreatedAtDesc(
            Long fromWalletId,
            Long toWalletId,
            Pageable pageable
    );

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);
}