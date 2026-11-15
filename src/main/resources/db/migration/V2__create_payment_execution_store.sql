-- R6.3 Regional-owned Payment Execution technical persistence.
-- This is NOT an Amplitude/Informix banking table and is not authoritative for banking facts.
CREATE TABLE regional_payment_execution (
  institution_code varchar(35) NOT NULL,
  payment_reference varchar(30) NOT NULL,
  idempotency_key varchar(128) NOT NULL,
  request_fingerprint char(64) NOT NULL,
  operation_code varchar(64) NOT NULL,
  event_number bigint NULL,
  accounting_date date NULL,
  outcome varchar(32) NULL,
  bank_reference varchar(128) NULL,
  reason_code varchar(128) NULL,
  result_json jsonb NULL,
  created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
  completed_at timestamptz NULL,
  PRIMARY KEY (institution_code, idempotency_key),
  CONSTRAINT uk_payment_execution_payment_reference
    UNIQUE (institution_code, payment_reference)
);

CREATE INDEX ix_payment_execution_payment_reference
  ON regional_payment_execution (institution_code, payment_reference);
