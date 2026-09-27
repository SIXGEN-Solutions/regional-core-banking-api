package com.regional.corebanking.confirmation.application.port.out;

import com.regional.corebanking.confirmation.domain.ConfirmationChallenge;
import java.util.Optional;
import java.util.function.Supplier;

public interface IdempotencyRepository {
    ConfirmationChallenge execute(String key, String operation, String fingerprint,
                                  Supplier<ConfirmationChallenge> action);
    Optional<Entry> find(String key);
    record Entry(String key, String operation, String fingerprint, ConfirmationChallenge result) {}
}
