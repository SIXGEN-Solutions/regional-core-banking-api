package com.regional.corebanking.confirmation.infrastructure;
import com.regional.corebanking.confirmation.application.exception.ChallengeNotFoundException;
import com.regional.corebanking.confirmation.application.port.out.ChallengeRepository;
import com.regional.corebanking.confirmation.domain.ConfirmationChallenge;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
public final class InMemoryChallengeRepository implements ChallengeRepository {
    private final ConcurrentHashMap<String,ConfirmationChallenge> values=new ConcurrentHashMap<>();
    public ConfirmationChallenge save(ConfirmationChallenge c){ values.put(c.challengeReference(),c); return c; }
    public Optional<ConfirmationChallenge> find(String r){ return Optional.ofNullable(values.get(r)); }
    public ConfirmationChallenge update(String r,UnaryOperator<ConfirmationChallenge> mutation){
        if(!values.containsKey(r)) throw new ChallengeNotFoundException(r);
        return values.compute(r,(k,v)->mutation.apply(v));
    }
}
