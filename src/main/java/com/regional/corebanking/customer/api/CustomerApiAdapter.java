package com.regional.corebanking.customer.api;

import com.regional.corebanking.customer.application.port.in.CustomerVerificationUseCase;
import com.regional.corebanking.generated.api.CustomersApi;
import com.regional.corebanking.generated.model.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class CustomerApiAdapter implements CustomersApi {

    private final CustomerVerificationUseCase useCase;
    private final CustomerApiMapper mapper;

    public CustomerApiAdapter(
            CustomerVerificationUseCase useCase,
            CustomerApiMapper mapper
    ) {
        this.useCase = useCase;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<CustomerSearchResult> searchCustomers(
            UUID xCorrelationID,
            String financialInstitutionCode,
            String niu,
            String customerNumber
    ) {
        return ResponseEntity.ok(mapper.toSearchResult(
                useCase.searchCustomers(financialInstitutionCode, niu, customerNumber)
        ));
    }

    @Override
    public ResponseEntity<CustomerIdentity> getCustomer(
            UUID xCorrelationID,
            String customerReference,
            String xFinancialInstitutionCode
    ) {
        return ResponseEntity.ok(mapper.toIdentity(
                useCase.getCustomer(xFinancialInstitutionCode, customerReference)
        ));
    }

    @Override
    public ResponseEntity<AccountSearchResult> getCustomerAccounts(
            UUID xCorrelationID,
            String customerReference,
            String xFinancialInstitutionCode,
            String rib,
            String iban
    ) {
        // Endpoint remains wired but is explicitly reserved for the future Account coverage.
        return ResponseEntity.ok(mapper.toAccountSearchResult(
                useCase.getCustomerAccounts(
                        xFinancialInstitutionCode,
                        customerReference,
                        rib,
                        iban
                )
        ));
    }

    @Override
    public ResponseEntity<CustomerVerificationResult> verifyCustomer(
            UUID xCorrelationID,
            CustomerVerificationRequest customerVerificationRequest
    ) {
        return ResponseEntity.ok(mapper.toVerificationResult(
                useCase.verifyCustomer(mapper.toDomain(customerVerificationRequest))
        ));
    }
}
