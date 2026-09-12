package com.razvan.digital_wallet_api.dto;

import com.razvan.digital_wallet_api.entity.Currency;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class TransactionResponse {

    private Long id;
    private Long fromWalletId;
    private Long toWalletId;
    private BigDecimal amount;
    private Currency currency;
    private String type;
    private String status;
    private LocalDateTime createdAt;
    private String description;

    public TransactionResponse(
            Long id,
            Long fromWalletId,
            Long toWalletId,
            BigDecimal amount,
            Currency currency,
            String type,
            String status,
            LocalDateTime createdAt,
            String description) {

        this.id = id;
        this.fromWalletId = fromWalletId;
        this.toWalletId = toWalletId;
        this.amount = amount;
        this.currency = currency;
        this.type = type;
        this.status = status;
        this.createdAt = createdAt;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public Long getFromWalletId() {
        return fromWalletId;
    }

    public Long getToWalletId() {
        return toWalletId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Currency getCurrency() {
        return currency;
    }

    public String getType() {
        return type;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    public String getDescription() {
        return description;
    }
}