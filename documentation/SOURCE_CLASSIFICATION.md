# Source Classification

This repository uses explicit source ownership to prevent consumer/reference material from silently becoming Regional canonical behavior.

## REGIONAL_CANONICAL

Regional-owned material maintained in this repository. A Regional OpenAPI file is canonical for implementation only after explicit human approval. Draft or pending material is not implementation-ready.

## SIXPAY_REFERENCE

Read-only consumer/compatibility evidence copied from `SIXGEN-Solutions/sixpay-connect`. It may be used to verify retained protocol compatibility but is never the source model of the Regional API. SIXPAY-side approval metadata does not transfer to Regional.

## BANK_REFERENCE

Evidence supplied by La Régionale concerning Amplitude/Informix behavior, mappings, procedures, security or banking rules. It becomes authoritative only after status and applicability are confirmed.

## HISTORICAL

Superseded evidence kept only for traceability. It must not drive active implementation.

## DEFERRED

Known material intentionally excluded from the active lot. It must not be inferred or recreated to unblock implementation.

## Conflict rule

When sources conflict, record and escalate the conflict. Do not silently reconcile or select a behavior.
