package com.razvan.digital_wallet_api.exception;

public class WalletAlreadyExistsException extends RuntimeException {

    public WalletAlreadyExistsException(String message) {
        super(message);
    }
}
