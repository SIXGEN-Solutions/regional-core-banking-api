package com.regional.corebanking.confirmation.infrastructure;

import com.regional.corebanking.confirmation.application.port.out.ConfirmationDeliveryPort;
import com.regional.corebanking.confirmation.domain.ConfirmationChallenge;
import com.regional.corebanking.confirmation.domain.DeliveryChannel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CompositeConfirmationDeliveryAdapterTest {

    @Test
    void exposes_union_of_enabled_channels() {
        var composite = new CompositeConfirmationDeliveryAdapter(List.of(
                stub(Set.of(DeliveryChannel.EMAIL)),
                stub(Set.of(DeliveryChannel.SMS))
        ));

        assertThat(composite.enabledChannels())
                .containsExactlyInAnyOrder(DeliveryChannel.EMAIL, DeliveryChannel.SMS);
    }

    private static ConfirmationDeliveryPort stub(Set<DeliveryChannel> channels) {
        return new ConfirmationDeliveryPort() {
            @Override
            public Set<DeliveryChannel> enabledChannels() {
                return channels;
            }

            @Override
            public Outcome dispatch(String financialInstitutionCode,
                                    ConfirmationChallenge challenge,
                                    char[] otp) {
                return Outcome.ACCEPTED;
            }
        };
    }
}
