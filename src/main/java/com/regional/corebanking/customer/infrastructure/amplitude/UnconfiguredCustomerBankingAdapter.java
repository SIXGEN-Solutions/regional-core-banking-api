package com.regional.corebanking.customer.infrastructure.amplitude;

import com.regional.corebanking.customer.application.port.out.CustomerBankingPort;
import com.regional.corebanking.customer.domain.BankAccount;
import com.regional.corebanking.customer.domain.CustomerIdentity;
import com.regional.corebanking.customer.domain.CustomerSummary;

import java.util.List;

public final class UnconfiguredCustomerBankingAdapter implements CustomerBankingPort {

    private static UnsupportedOperationException missingEvidence() {
        return new UnsupportedOperationException(
                "Customer/Account banking access is not configured until approved La Régionale banking evidence is available"
        );
    }

    @Override
    public List<CustomerSummary> searchCustomers(
            String financialInstitutionCode,
            String niu,
            String customerNumber
    ) {
        throw missingEvidence();
    }

    @Override
    public CustomerIdentity getCustomer(
            String financialInstitutionCode,
            String customerReference
    ) {
        throw missingEvidence();
    }

    @Override
    public List<BankAccount> getCustomerAccounts(
            String financialInstitutionCode,
            String customerReference,
            String rib,
            String iban
    ) {
        throw missingEvidence();
    }

    @Override
    public BankAccount findAccountByReference(String accountReference) {
        throw missingEvidence();
    }
}
