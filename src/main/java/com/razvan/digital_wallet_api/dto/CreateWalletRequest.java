package com.razvan.digital_wallet_api.dto;

import jakarta.validation.constraints.NotNull;
import com.razvan.digital_wallet_api.entity.Currency;

public class CreateWalletRequest {

    @NotNull(message = "User id is required")
    private Long userId;

    @NotNull(message = "Currency is required")
    private Currency currency;

    public CreateWalletRequest() {
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Currency getCurrency() {
        return currency;
    }

    public void setCurrency(Currency currency) {
        this.currency = currency;
    }
}