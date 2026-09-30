package com.razvan.digital_wallet_api.dto;

import com.razvan.digital_wallet_api.entity.Currency;

import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonFormat;

public class WalletResponse {

    private Long id;
    private Long userId;
    private Currency currency;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal balance;

    public WalletResponse() {
    }

    public WalletResponse(
            Long id,
            Long userId,
            Currency currency,
            BigDecimal balance) {

        this.id = id;
        this.userId = userId;
        this.currency = currency;
        this.balance = balance;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Currency getCurrency() {
        return currency;
    }

    public BigDecimal getBalance() {
        return balance;
    }
}