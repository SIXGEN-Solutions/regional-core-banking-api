package com.regional.corebanking.confirmation.application.port.out;

import com.regional.corebanking.confirmation.domain.ConfirmationChallenge;
import java.util.Optional;
import java.util.function.Supplier;

public interface IdempotencyRepository {
    ConfirmationChallenge execute(String institution, String key, String operation,
                                  String fingerprint, Supplier<ConfirmationChallenge> action);
    Optional<Entry> find(String institution, String key);
    record Entry(String institution, String key, String operation, String fingerprint,
                 ConfirmationChallenge result) {}
}
