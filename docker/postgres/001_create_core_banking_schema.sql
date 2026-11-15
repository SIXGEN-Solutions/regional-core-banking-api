-- Local Docker technical persistence bootstrap only.
-- This is not an Amplitude/Informix banking schema.

CREATE SCHEMA IF NOT EXISTS core_banking
    AUTHORIZATION regional_core_banking_app;
