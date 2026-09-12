package com.razvan.digital_wallet_api.exception;

public class SameWalletTransferException extends RuntimeException {

    public SameWalletTransferException(String message) {
        super(message);
    }
}