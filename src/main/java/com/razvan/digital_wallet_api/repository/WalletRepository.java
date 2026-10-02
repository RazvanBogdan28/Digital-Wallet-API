package com.razvan.digital_wallet_api.repository;

import com.razvan.digital_wallet_api.entity.Currency;
import com.razvan.digital_wallet_api.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    List<Wallet> findByUserId(Long userId);

    boolean existsByUserIdAndCurrency(Long userId, Currency currency);

    @Query("SELECT w.user.id FROM Wallet w WHERE w.id = :id")
    Optional<Long> findOwnerIdById(@Param("id") Long id);

    @Query(
            value = "SELECT w.* FROM wallets w WHERE w.id = :id FOR UPDATE OF w",
            nativeQuery = true
    )
    Optional<Wallet> findByIdForUpdate(@Param("id") Long id);
}