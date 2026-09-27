package com.regional.corebanking.confirmation.infrastructure;

import com.regional.corebanking.confirmation.application.exception.IdempotencyConflictException;
import com.regional.corebanking.confirmation.application.port.out.IdempotencyRepository;
import com.regional.corebanking.confirmation.domain.ConfirmationChallenge;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class InMemoryIdempotencyRepository implements IdempotencyRepository {
    private final ConcurrentHashMap<String, Entry> values = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    @Override
    public ConfirmationChallenge execute(String key, String operation, String fingerprint,
                                         Supplier<ConfirmationChallenge> action) {
        Object lock = locks.computeIfAbsent(key, ignored -> new Object());
        synchronized (lock) {
            Entry existing = values.get(key);
            if (existing != null) {
                if (!existing.operation().equals(operation)
                        || !MessageDigest.isEqual(
                        existing.fingerprint().getBytes(StandardCharsets.US_ASCII),
                        fingerprint.getBytes(StandardCharsets.US_ASCII))) {
                    throw new IdempotencyConflictException();
                }
                return existing.result();
            }
            ConfirmationChallenge result = action.get();
            values.put(key, new Entry(key, operation, fingerprint, result));
            return result;
        }
    }

    @Override
    public Optional<Entry> find(String key) {
        return Optional.ofNullable(values.get(key));
    }
}
