package com.regional.corebanking.confirmation.infrastructure;
import com.regional.corebanking.confirmation.application.port.out.IdempotencyRepository;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
public final class InMemoryIdempotencyRepository implements IdempotencyRepository {
    private final ConcurrentHashMap<String,Entry> values=new ConcurrentHashMap<>();
    public Optional<Entry> find(String key){ return Optional.ofNullable(values.get(key)); }
    public void save(Entry entry){ values.putIfAbsent(entry.key(),entry); }
}
