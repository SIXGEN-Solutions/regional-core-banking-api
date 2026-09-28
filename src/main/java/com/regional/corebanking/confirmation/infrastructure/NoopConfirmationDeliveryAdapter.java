package com.regional.corebanking.confirmation.infrastructure;

import com.regional.corebanking.confirmation.application.port.out.ConfirmationDeliveryPort;
import com.regional.corebanking.confirmation.domain.ConfirmationChallenge;
import com.regional.corebanking.confirmation.domain.DeliveryChannel;
import java.util.Set;

public final class NoopConfirmationDeliveryAdapter implements ConfirmationDeliveryPort {
    private final Set<DeliveryChannel> channels;

    public NoopConfirmationDeliveryAdapter(Set<DeliveryChannel> channels) {
        this.channels = Set.copyOf(channels);
    }

    @Override
    public Set<DeliveryChannel> enabledChannels() {
        return channels;
    }

    @Override
    public Outcome dispatch(String financialInstitutionCode, ConfirmationChallenge challenge, char[] otp) {
        return Outcome.DELIVERED;
    }
}
