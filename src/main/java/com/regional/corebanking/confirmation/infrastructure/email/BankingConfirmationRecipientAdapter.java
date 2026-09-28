package com.regional.corebanking.confirmation.infrastructure.email;

import com.regional.corebanking.confirmation.application.port.out.ConfirmationRecipientPort;
import com.regional.corebanking.customer.application.port.out.CustomerBankingPort;
import com.regional.corebanking.customer.domain.CustomerIdentity;

import java.util.Optional;

public final class BankingConfirmationRecipientAdapter implements ConfirmationRecipientPort {
    private final CustomerBankingPort customers;

    public BankingConfirmationRecipientAdapter(CustomerBankingPort customers) {
        this.customers = customers;
    }

    @Override
    public Optional<String> findEmail(String financialInstitutionCode, String customerReference) {
        CustomerIdentity customer = customers.getCustomer(financialInstitutionCode, customerReference);
        if (customer == null) return Optional.empty();
        return Optional.ofNullable(customer.email()).map(String::strip).filter(v -> !v.isEmpty());
    }
}
