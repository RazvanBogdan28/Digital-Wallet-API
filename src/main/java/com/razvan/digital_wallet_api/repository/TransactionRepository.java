package com.razvan.digital_wallet_api.repository;

import com.razvan.digital_wallet_api.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TransactionRepository
        extends JpaRepository<Transaction, Long> {

    @Query(
            value = """
                    SELECT t
                    FROM Transaction t
                    WHERE t.fromWallet.id = :fromWalletId
                       OR t.toWallet.id = :toWalletId
                    ORDER BY t.createdAt DESC, t.id DESC
                    """,
            countQuery = """
                    SELECT COUNT(t)
                    FROM Transaction t
                    WHERE t.fromWallet.id = :fromWalletId
                       OR t.toWallet.id = :toWalletId
                    """
    )
    Page<Transaction> findByFromWalletIdOrToWalletIdOrderByCreatedAtDesc(
            @Param("fromWalletId") Long fromWalletId,
            @Param("toWalletId") Long toWalletId,
            Pageable pageable
    );

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);
}