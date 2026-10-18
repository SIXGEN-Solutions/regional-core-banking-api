# R6.6 — Legacy JDBC binding alignment

Source: `bkeve_short.java` 233 declarations and `getQueryValues` positional list, verified against the existing 233-column matrix.
JDBC bindings: String/CHAR, Double/DECIMAL (via BigDecimal.valueOf), Date/DATE, Integer/INTEGER.
SQL NULL binds to source-derived column type. The legacy Double itself may already have lost decimal precision.
Correct the test leg roles (debit first, credit second).

Not a full payment activation: the active Regional contract carries 13 input properties, not the complete legacy BKEVE.
Financial posting and recovery stay disabled until the contract/data path and authoritative recovery are reconciled.
