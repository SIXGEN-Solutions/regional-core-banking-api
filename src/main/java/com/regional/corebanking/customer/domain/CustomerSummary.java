package com.regional.corebanking.customer.domain;

public record CustomerSummary(
        String customerReference,
        String customerNumber,
        String financialInstitutionCode,
        String niu,
        String legalName
) {
}
