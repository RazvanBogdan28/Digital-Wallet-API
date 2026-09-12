CREATE TABLE users (
                       id BIGSERIAL PRIMARY KEY,
                       first_name VARCHAR(255) NOT NULL,
                       last_name VARCHAR(255) NOT NULL,
                       email VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE wallets (
                         id BIGSERIAL PRIMARY KEY,
                         version BIGINT NOT NULL DEFAULT 0,
                         currency VARCHAR(255) NOT NULL,
                         balance NUMERIC(19, 2) NOT NULL,
                         user_id BIGINT NOT NULL,

                         CONSTRAINT fk_wallet_user
                             FOREIGN KEY (user_id)
                                 REFERENCES users(id),

                         CONSTRAINT uk_wallet_user_currency
                             UNIQUE (user_id, currency)
);

CREATE TABLE transactions (
                              id BIGSERIAL PRIMARY KEY,
                              from_wallet_id BIGINT NOT NULL,
                              to_wallet_id BIGINT NOT NULL,
                              amount NUMERIC(19, 2) NOT NULL,
                              currency VARCHAR(255) NOT NULL,
                              type VARCHAR(255) NOT NULL,
                              status VARCHAR(255) NOT NULL,
                              created_at TIMESTAMP NOT NULL,
                              idempotency_key VARCHAR(255) NOT NULL UNIQUE,

                              CONSTRAINT fk_transaction_from_wallet
                                  FOREIGN KEY (from_wallet_id)
                                      REFERENCES wallets(id),

                              CONSTRAINT fk_transaction_to_wallet
                                  FOREIGN KEY (to_wallet_id)
                                      REFERENCES wallets(id)
);