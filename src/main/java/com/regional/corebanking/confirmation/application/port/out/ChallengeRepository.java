package com.regional.corebanking.confirmation.application.port.out;

import com.regional.corebanking.confirmation.domain.ConfirmationChallenge;
import java.util.Optional;
import java.util.function.UnaryOperator;

public interface ChallengeRepository {
    ConfirmationChallenge save(String institution, ConfirmationChallenge challenge);
    Optional<ConfirmationChallenge> find(String institution, String reference);
    ConfirmationChallenge update(String institution, String reference,
                                 UnaryOperator<ConfirmationChallenge> mutation);
}
