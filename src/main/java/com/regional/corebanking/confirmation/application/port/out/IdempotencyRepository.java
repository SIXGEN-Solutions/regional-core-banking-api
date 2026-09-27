package com.regional.corebanking.confirmation.application.port.out;
import com.regional.corebanking.confirmation.domain.ConfirmationChallenge;
import java.util.Optional;
public interface IdempotencyRepository {
    Optional<Entry> find(String key);
    void save(Entry entry);
    record Entry(String key,String operation,String fingerprint,ConfirmationChallenge result) {}
}
