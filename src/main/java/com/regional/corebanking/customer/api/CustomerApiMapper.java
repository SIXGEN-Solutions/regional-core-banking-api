package com.regional.corebanking.customer.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.regional.corebanking.customer.domain.BankAccount;
import com.regional.corebanking.customer.domain.CustomerIdentity;
import com.regional.corebanking.customer.domain.CustomerSummary;
import com.regional.corebanking.customer.domain.CustomerVerification;
import com.regional.corebanking.generated.model.AccountSearchResult;
import com.regional.corebanking.generated.model.CustomerSearchResult;
import com.regional.corebanking.generated.model.CustomerVerificationRequest;
import com.regional.corebanking.generated.model.CustomerVerificationResult;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CustomerApiMapper {

    private static final String SOURCE = "AMPLITUDE";

    private final ObjectMapper objectMapper;

    public CustomerApiMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CustomerSearchResult toSearchResult(List<CustomerSummary> customers) {
        List<Map<String, Object>> payload = customers.stream()
                .map(this::customerSummary)
                .toList();

        return objectMapper.convertValue(
                Map.of("customers", payload, "resultCount", payload.size()),
                CustomerSearchResult.class
        );
    }

    public com.regional.corebanking.generated.model.CustomerIdentity toIdentity(
            CustomerIdentity identity
    ) {
        return objectMapper.convertValue(
                identityPayload(identity),
                com.regional.corebanking.generated.model.CustomerIdentity.class
        );
    }

    public AccountSearchResult toAccountSearchResult(List<BankAccount> accounts) {
        List<Map<String, Object>> payload = accounts.stream()
                .map(this::accountSummary)
                .toList();

        return objectMapper.convertValue(
                Map.of("accounts", payload, "resultCount", payload.size()),
                AccountSearchResult.class
        );
    }

    public CustomerVerification.Request toDomain(CustomerVerificationRequest request) {
        Map<String, Object> payload = objectMapper.convertValue(
                request,
                new TypeReference<Map<String, Object>>() { }
        );

        return new CustomerVerification.Request(
                string(payload.get("accountReference")),
                string(payload.get("expectedNiu")),
                string(payload.get("expectedAccountHolder")),
                stringList(payload.get("requiredKycFields")),
                instant(payload.get("requestedAt"))
        );
    }

    public CustomerVerificationResult toVerificationResult(
            CustomerVerification.Result result
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("verificationId", result.verificationId());
        payload.put("verifiedAt", result.verifiedAt());
        payload.put("source", SOURCE);
        payload.put("outcome", result.outcome().name());
        payload.put("customerReference", result.customerReference());
        payload.put("accountReference", result.accountReference());
        payload.put("checks", result.checks().stream().map(this::check).toList());
        payload.put("identity",
                result.identity() == null ? null : identityPayload(result.identity()));
        payload.put("account",
                result.account() == null ? null : accountSummary(result.account()));

        return objectMapper.convertValue(payload, CustomerVerificationResult.class);
    }

    private Map<String, Object> customerSummary(CustomerSummary customer) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("customerReference", customer.customerReference());
        payload.put("customerNumber", customer.customerNumber());
        payload.put("financialInstitutionCode", customer.financialInstitutionCode());
        payload.put("niu", customer.niu());
        payload.put("legalName", customer.legalName());
        return payload;
    }

    private Map<String, Object> identityPayload(CustomerIdentity identity) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("customerReference", identity.customerReference());
        payload.put("customerNumber", identity.customerNumber());
        payload.put("financialInstitutionCode", identity.financialInstitutionCode());
        payload.put("niu", identity.niu());
        payload.put("legalName", identity.legalName());
        payload.put("phoneNumber", identity.phoneNumber());
        payload.put("email", identity.email());
        payload.put("kycStatus", identity.kycStatus().name());
        payload.put("source", SOURCE);
        payload.put("retrievedAt", identity.retrievedAt());
        return payload;
    }

    private Map<String, Object> accountSummary(BankAccount account) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("accountReference", account.accountReference());
        payload.put("customerReference", account.customerReference());
        payload.put("status", transportStatus(account.status()));
        payload.put("currency", account.currency());
        return payload;
    }

    private Map<String, Object> check(CustomerVerification.Check check) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", check.type().name());
        payload.put("result", check.result().name());
        payload.put("reasonCode", check.reasonCode());
        payload.put("checkedAt", check.checkedAt());
        return payload;
    }

    private static String transportStatus(BankAccount.AccountStatus status) {
        return switch (status) {
            case ACTIVE -> "ACTIVE";
            case INACTIVE, DORMANT -> "INACTIVE";
            case BLOCKED, FROZEN -> "BLOCKED";
            case CLOSED -> "CLOSED";
            case UNKNOWN -> "UNKNOWN";
        };
    }

    private static String string(Object value) {
        return value == null ? null : value.toString();
    }

    private static List<String> stringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> values)) {
            throw new IllegalArgumentException("requiredKycFields must be an array");
        }
        return values.stream().map(String::valueOf).toList();
    }

    private static Instant instant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        return Instant.parse(value.toString());
    }
}
