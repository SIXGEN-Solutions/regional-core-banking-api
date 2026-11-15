package com.regional.corebanking.confirmation.infrastructure;

import com.regional.corebanking.confirmation.application.port.out.ConfirmationDeliveryPort;
import com.regional.corebanking.confirmation.domain.ConfirmationChallenge;
import com.regional.corebanking.confirmation.domain.DeliveryChannel;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class CompositeConfirmationDeliveryAdapter implements ConfirmationDeliveryPort {

    private final List<ConfirmationDeliveryPort> delegates;

    public CompositeConfirmationDeliveryAdapter(List<ConfirmationDeliveryPort> delegates) {
        this.delegates = List.copyOf(delegates);
    }

    @Override
    public Set<DeliveryChannel> enabledChannels() {
        EnumSet<DeliveryChannel> channels = EnumSet.noneOf(DeliveryChannel.class);
        for (ConfirmationDeliveryPort delegate : delegates) {
            channels.addAll(delegate.enabledChannels());
        }
        return Set.copyOf(channels);
    }

    @Override
    public Outcome dispatch(String financialInstitutionCode, ConfirmationChallenge challenge, char[] otp) {
        boolean accepted = false;
        boolean unknown = false;

        for (ConfirmationDeliveryPort delegate : delegates) {
            if (delegate.enabledChannels().isEmpty()) {
                continue;
            }
            Outcome outcome = delegate.dispatch(financialInstitutionCode, challenge, otp);
            if (outcome == Outcome.ACCEPTED) accepted = true;
            if (outcome == Outcome.UNKNOWN) unknown = true;
        }

        // A positive outcome on one channel must not hide an indeterminate
        // submission on another channel. Channel-level results are not yet
        // represented by the approved singular OpenAPI deliveryChannel.
        if (unknown) return Outcome.UNKNOWN;
        return accepted ? Outcome.ACCEPTED : Outcome.CONFIRMED_FAILURE;
    }
}
