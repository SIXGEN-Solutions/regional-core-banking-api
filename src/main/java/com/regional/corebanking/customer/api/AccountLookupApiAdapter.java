package com.regional.corebanking.customer.api;

import com.regional.corebanking.customer.application.port.in.CustomerVerificationUseCase;
import com.regional.corebanking.customer.domain.BankAccount;
import com.regional.corebanking.generated.model.AccountLookupResult;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Read-only Regional account lookup; no SIXPAY-specific classes. */
@RestController
public final class AccountLookupApiAdapter {
    private final CustomerVerificationUseCase useCase;
    private final CustomerApiMapper mapper;

    public AccountLookupApiAdapter(CustomerVerificationUseCase useCase, CustomerApiMapper mapper) {
        this.useCase = useCase;
        this.mapper = mapper;
    }

    @GetMapping(value = "/api/v1/getaccountinfo/{branchid}/{accountnumber}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AccountLookupResult> getAccountByBranchAndNumber(
            @RequestHeader("X-Correlation-ID") UUID correlationId,
            @RequestHeader("X-Financial-Institution-Code") String institutionCode,
            @PathVariable("branchid") String branchId,
            @PathVariable("accountnumber") String accountNumber) {
        BankAccount account = useCase.findAccountByAgencyAndNumber(institutionCode, branchId, accountNumber);
        return ResponseEntity.ok(mapper.toAccountLookupResult(account));
    }
}
