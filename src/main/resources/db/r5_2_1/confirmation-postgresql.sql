-- R5.2.1 Regional-owned technical persistence. Not an Amplitude schema.
CREATE TABLE IF NOT EXISTS regional_confirmation_challenge (
  institution_code varchar(35) NOT NULL,
  challenge_reference varchar(128) NOT NULL,
  payment_reference varchar(30) NOT NULL,
  customer_reference varchar(100) NOT NULL,
  debtor_account_reference varchar(100) NOT NULL,
  amount numeric(19,4) NOT NULL CHECK (amount > 0),
  currency char(3) NOT NULL,
  otp_verifier varchar(512) NOT NULL,
  otp_key_version varchar(128) NOT NULL,
  challenge_status varchar(32) NOT NULL,
  business_code varchar(64) NOT NULL,
  failed_attempts integer NOT NULL CHECK (failed_attempts >= 0),
  replacement_count integer NOT NULL CHECK (replacement_count >= 0),
  delivery_channels varchar(64) NOT NULL,
  created_at timestamptz NOT NULL,
  expires_at timestamptz NOT NULL,
  verified_at timestamptz NULL,
  replaced_at timestamptz NULL,
  revoked_at timestamptz NULL,
  PRIMARY KEY (institution_code, challenge_reference)
);
CREATE INDEX IF NOT EXISTS ix_confirmation_payment
  ON regional_confirmation_challenge (institution_code, payment_reference);

CREATE TABLE IF NOT EXISTS regional_confirmation_idempotency (
  institution_code varchar(35) NOT NULL,
  idempotency_key varchar(128) NOT NULL,
  operation varchar(32) NOT NULL,
  request_fingerprint char(64) NOT NULL,
  result_json jsonb NULL,
  created_at timestamptz NOT NULL,
  completed_at timestamptz NULL,
  PRIMARY KEY (institution_code, idempotency_key)
);
