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
    private final ConcurrentHashMap<Key, Entry> values = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Key, Object> locks = new ConcurrentHashMap<>();

    public ConfirmationChallenge execute(String institution, String key, String operation,
                                         String fingerprint, Supplier<ConfirmationChallenge> action) {
        Key scoped = new Key(institution, key);
        Object lock = locks.computeIfAbsent(scoped, ignored -> new Object());
        synchronized (lock) {
            Entry existing = values.get(scoped);
            if (existing != null) {
                if (!existing.operation().equals(operation) || !MessageDigest.isEqual(
                        existing.fingerprint().getBytes(StandardCharsets.US_ASCII),
                        fingerprint.getBytes(StandardCharsets.US_ASCII))) {
                    throw new IdempotencyConflictException();
                }
                return existing.result();
            }
            ConfirmationChallenge result = action.get();
            values.put(scoped, new Entry(institution, key, operation, fingerprint, result));
            return result;
        }
    }
    public Optional<Entry> find(String institution, String key) {
        return Optional.ofNullable(values.get(new Key(institution, key)));
    }
    private record Key(String institution, String key) {}
}
