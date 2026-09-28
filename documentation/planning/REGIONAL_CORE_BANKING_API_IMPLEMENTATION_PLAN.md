# REGIONAL CORE BANKING API — Implementation Plan

## R0 — Bootstrap and source pack
Create the independent repository and load this starter pack. No server behavior yet.

## R1 — Regional contract baseline
Build `regional-core-banking-api-v1.yaml` from approved Regional banking capabilities and bank evidence. Use SIXPAY contracts and clients as compatibility evidence, not as the Regional model source. Resolve all placeholder schemas before code generation.

## R2 — Repository / Spring Boot bootstrap
Create Maven Wrapper, Java 21 Spring Boot project, package boundaries, config profiles, health endpoints, test foundation, Dockerfile and Compose skeleton.

## R3 — Contract generation and CI
Pin the OpenAPI generator/toolchain version after human approval. Generate API interfaces/transport DTOs into generated sources. Add OpenAPI validation, breaking-change detection, Maven verify, architecture tests, security scanning and Docker-build validation.

## R4 — Customer / Account Verification
Implement approved lookup and composite verification against bank evidence/Informix access. Include contract tests and consumer compatibility tests.

## R5 — Payment Confirmation / OTP
Implement the Regional-owned challenge lifecycle from the approved contract and
confirmed Regional mechanisms. Regional Core Banking API is authoritative for OTP
generation, secure verification, lifecycle and delivery orchestration. Plaintext OTP
is transient and is never persisted.

R5.2 sequence:
- R5.2.0: align validated decisions/documentation;
- R5.2.1: dedicated PostgreSQL technical persistence and multi-instance concurrency;
- R5.2.2: externalized/versioned HMAC secret integration;
- R5.2.3: EMAIL delivery through La Régionale-authorized mail infrastructure;
- R5.2.4: delivery outcome and timestamp semantics;
- R5.2.5: SMS database/Kannel adapter after authoritative bank mapping evidence;
- R5.2.6: robustness, recovery, security and integration closure tests.

Delivery channels are Regional server configuration, not consumer input. SMS and
EMAIL may both be enabled. The approved OpenAPI singular `deliveryChannel` is a
known divergence requiring separately approved contract evolution before wire change.

## R6 — Payment execution / context / recovery
Implement payment-event execution, authoritative recovery and approved banking-context lookups. No blind financial replay. No standalone funds-control API unless separately approved.

## R7 — Accounting T1
Implement accounting batch submission/recovery only from the approved contract and bank mapping evidence.

## R8 — TFJ / End-of-day
Implement Regional TFJ lookup and outbound callback client behavior after confirming callback integration/security parameters.

## R9 — Sandbox hardening and deployment
Finalize environment config, Informix connectivity, OAuth2/mTLS, trust material injection, observability, Docker Compose deployment and operational runbooks.

## Mandatory human decisions before code depending on them
- exact Regional V1 contract approval;
- generation tool/version and generated-code scope;
- Informix access mode, credentials, queries/procedures;
- exact banking field/procedure mappings;
- environment OAuth2/mTLS parameters;
- physical schema/migration details for the approved dedicated PostgreSQL technical persistence when implementing R5.2.1;
- release/versioning policy.
