package com.regional.corebanking.customer.api;

import com.regional.corebanking.customer.application.port.in.CustomerVerificationUseCase;
import com.regional.corebanking.customer.domain.BankAccount;
import com.regional.corebanking.generated.model.AccountLookupResult;
import org.springframework.beans.factory.annotation.Value;
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
    private final String configuredInstitutionCode;

    public AccountLookupApiAdapter(
            CustomerVerificationUseCase useCase,
            CustomerApiMapper mapper,
            @Value("${regional.banking.financial-institution-code:}") String configuredInstitutionCode) {
        this.useCase = useCase;
        this.mapper = mapper;
        this.configuredInstitutionCode = configuredInstitutionCode;
    }

    @GetMapping(value = "/api/v1/getaccountinfo/{branchid}/{accountnumber}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AccountLookupResult> getAccountByBranchAndNumber(
            @RequestHeader("X-Correlation-ID") UUID correlationId,
            @RequestHeader(value = "X-Financial-Institution-Code", required = false) String institutionCode,
            @PathVariable("branchid") String branchId,
            @PathVariable("accountnumber") String accountNumber) {
        // Missing header selects the existing bank deployment configuration.
        // A supplied (even blank or incorrect) header is never silently replaced.

        String effectiveInstitutionCode = institutionCode == null
                ? configuredInstitutionCode : institutionCode;
        BankAccount account = useCase.findAccountByAgencyAndNumber(
                effectiveInstitutionCode, branchId, accountNumber);
        return ResponseEntity.ok(mapper.toAccountLookupResult(account));
    }
}
