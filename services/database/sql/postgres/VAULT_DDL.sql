CREATE TABLE IF NOT EXISTS vault_aum
(
    state_key BYTEA                    NOT NULL CHECK (octet_length(state_key) = 32),
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    slot      BIGINT                   NOT NULL CHECK (slot > 0),
    supply    BIGINT                   NOT NULL CHECK (supply >= 0),
    base_aum  BIGINT                   NOT NULL,
    usd_aum   NUMERIC(20, 2)           NOT NULL,
    PRIMARY KEY (state_key, timestamp)
);

CREATE TYPE vault_positions_type AS ENUM (
    'TOKEN',
    'DRIFT_SPOT', 'DRIFT_PERP',
    'DRIFT_VAULT',
    'KAMINO_DEPOSIT', 'KAMINO_BORROW',
    'KAMINO_VAULT'
    );

CREATE TABLE IF NOT EXISTS vault_positions
(
    state_key   BYTEA                    NOT NULL CHECK (octet_length(state_key) = 32),
    timestamp   TIMESTAMP WITH TIME ZONE NOT NULL,
    account     BYTEA                    NOT NULL CHECK (octet_length(state_key) = 32),
    slot        BIGINT                   NOT NULL CHECK (slot > 0),
    type        vault_positions_type     NOT NULL,
    mint_owner  BYTEA,
    mint        BYTEA,
    state_owner BYTEA,
    state       BYTEA,
    amount      BIGINT                   NOT NULL,
    base_amount BIGINT                   NOT NULL,
    usd_amount  NUMERIC(20, 2)           NOT NULL,
    PRIMARY KEY (state_key, timestamp, account)
);
