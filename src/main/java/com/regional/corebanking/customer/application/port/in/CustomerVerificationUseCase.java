package com.regional.corebanking.customer.application.port.in;

import com.regional.corebanking.customer.domain.BankAccount;
import com.regional.corebanking.customer.domain.CustomerIdentity;
import com.regional.corebanking.customer.domain.CustomerSummary;
import com.regional.corebanking.customer.domain.CustomerVerification;

import java.util.List;

public interface CustomerVerificationUseCase {

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

    CustomerVerification.Result verifyCustomer(
            CustomerVerification.Request request
    );
}
