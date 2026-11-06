-- Regional-owned technical PostgreSQL persistence for Payment Event EVE reservations.
-- NOT an Amplitude / Informix / Oracle banking schema.
-- A reserved EVE is not proof that a banking event has been committed.
-- Used by JdbcPaymentEventNumberReservationAdapter.
CREATE TABLE core_banking.api_eodprocess (
    id TEXT PRIMARY KEY,
    lasteve VARCHAR(6),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_api_eodprocess_id_not_blank
        CHECK (length(btrim(id)) > 0),
    CONSTRAINT ck_api_eodprocess_lasteve_format
        CHECK (lasteve IS NULL OR lasteve ~ '^[0-9]{6}$'),
    CONSTRAINT ck_api_eodprocess_lasteve_positive
        CHECK (lasteve IS NULL OR lasteve <> '000000'),
    CONSTRAINT ck_api_eodprocess_version_nonnegative
        CHECK (version >= 0)
);

COMMENT ON TABLE core_banking.api_eodprocess IS
    'Technical EVE high-water mark per operation code; not financial execution evidence';
