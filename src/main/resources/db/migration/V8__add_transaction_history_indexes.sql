CREATE INDEX idx_transactions_from_wallet_created_at_id
    ON transactions (from_wallet_id, created_at DESC, id DESC);

CREATE INDEX idx_transactions_to_wallet_created_at_id
    ON transactions (to_wallet_id, created_at DESC, id DESC);