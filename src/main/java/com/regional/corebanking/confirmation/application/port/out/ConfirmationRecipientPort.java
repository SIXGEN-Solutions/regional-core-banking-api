package com.regional.corebanking.confirmation.application.port.out;

import java.util.Optional;

public interface ConfirmationRecipientPort {

    Optional<String> findEmail(
            String financialInstitutionCode,
            String customerReference
    );

    default Optional<String> findPhone(
            String financialInstitutionCode,
            String customerReference
    ) {
        return Optional.empty();
    }
}