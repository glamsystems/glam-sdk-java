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
