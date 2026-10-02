package com.razvan.digital_wallet_api.dto;

import java.time.Instant;
import java.util.List;

public class WalletTransactionWindowResponse {

    private final WalletResponse wallet;
    private final List<TransactionResponse> items;
    private final long totalElements;
    private final boolean complete;
    private final Instant snapshotAt;

    public WalletTransactionWindowResponse(
            WalletResponse wallet,
            List<TransactionResponse> items,
            long totalElements,
            Instant snapshotAt
    ) {
        this.wallet = wallet;
        this.items = List.copyOf(items);
        this.totalElements = totalElements;
        this.complete = items.size() == totalElements;
        this.snapshotAt = snapshotAt;
    }

    public WalletResponse getWallet() {
        return wallet;
    }

    public List<TransactionResponse> getItems() {
        return items;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public boolean isComplete() {
        return complete;
    }

    public Instant getSnapshotAt() {
        return snapshotAt;
    }
}