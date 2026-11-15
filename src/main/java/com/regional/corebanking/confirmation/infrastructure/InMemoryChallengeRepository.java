package com.regional.corebanking.confirmation.infrastructure;

import com.regional.corebanking.confirmation.application.exception.ChallengeNotFoundException;
import com.regional.corebanking.confirmation.application.port.out.ChallengeRepository;
import com.regional.corebanking.confirmation.domain.ConfirmationChallenge;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

public final class InMemoryChallengeRepository implements ChallengeRepository {
    private final ConcurrentHashMap<Key, ConfirmationChallenge> values = new ConcurrentHashMap<>();

    public ConfirmationChallenge save(String institution, ConfirmationChallenge challenge) {
        values.put(new Key(institution, challenge.challengeReference()), challenge);
        return challenge;
    }
    public Optional<ConfirmationChallenge> find(String institution, String reference) {
        return Optional.ofNullable(values.get(new Key(institution, reference)));
    }
    public ConfirmationChallenge update(String institution, String reference,
                                        UnaryOperator<ConfirmationChallenge> mutation) {
        Key key = new Key(institution, reference);
        if (!values.containsKey(key)) throw new ChallengeNotFoundException(reference);
        return values.compute(key, (ignored, current) -> mutation.apply(current));
    }
    private record Key(String institution, String reference) {}
}
