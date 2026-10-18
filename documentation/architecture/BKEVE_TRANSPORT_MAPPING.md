# R6.6 — BKEVE 233-property transport

Source: bank-supplied `bkeve_short.java` (`getQueryValues()`), with repository
`R6_6_1_BKEVE_MAPPING_233.csv` as the checked 233-position matrix.

This change extends `PaymentProviderEvent` in the approved Regional envelope with
all 233 named BKEVE properties. The former 13 property names remain readable;
`paymentReference` remains required, while further required fields depend on the
operation and are validated at the financial execution boundary.

The domain `BkeveEvent` is a column-name keyed model, not a consumer JPA entity.
It retains supplied non-null values without inventing defaults. `EVE`, `DCO`,
`DVA1`, `DVA2` are reserved for Regional and rejected if supplied by a client.

`BkeveJdbcParameterMapper` preserves caller values, checks inconsistencies against
existing aliases, and still requires a complete approved 233-column value vector
before any INSERT. Existing technical PostgreSQL idempotence continues to precede
financial execution. No new Informix posting path is activated by this change.

Known limits requiring separate review:
- Generated nullable Java DTOs may not distinguish missing JSON properties from
  explicitly JSON-null properties; both are treated as absent at the HTTP boundary.
- Existing alias `currency` can be ISO-coded, while `dev` is the bank code;
  their business equivalence is NOT assumed.
- Existing `debitAmount` and `creditAmount` semantic mappings remain; distinct
  `MHT1/MON1` or `MHT2/MON2` caller values require dedicated approval.
- Any consumer relying on the previous `required` set needs compatibility tests.
- This evolution does not resolve uncertain Informix commit recovery or EVE
  coexistence with other banking producers.
