package com.razvan.digital_wallet_api.repository;

import com.razvan.digital_wallet_api.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import com.razvan.digital_wallet_api.entity.Currency;

import java.util.List;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    List<Wallet> findByUserId(Long userId);

    boolean existsByUserIdAndCurrency(Long userId, Currency currency);
}