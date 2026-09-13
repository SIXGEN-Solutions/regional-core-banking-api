package com.regional.corebanking.customer.application.port.out;

import com.regional.corebanking.customer.domain.BankAccount;
import com.regional.corebanking.customer.domain.CustomerIdentity;
import com.regional.corebanking.customer.domain.CustomerSummary;

import java.util.List;

public interface CustomerBankingPort {

    List<CustomerSummary> searchCustomers(
            String financialInstitutionCode,
            String niu,
            String customerNumber
    );

    CustomerIdentity getCustomer(
            String financialInstitutionCode,
            String customerReference
    );

    List<BankAccount> getCustomerAccounts(
            String financialInstitutionCode,
            String customerReference,
            String rib,
            String iban
    );

    BankAccount findAccountByReference(String accountReference);
}
