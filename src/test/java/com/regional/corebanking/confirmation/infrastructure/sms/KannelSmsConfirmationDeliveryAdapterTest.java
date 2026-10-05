package com.regional.corebanking.confirmation.infrastructure.sms;

import com.regional.corebanking.confirmation.application.port.out.ConfirmationRecipientPort;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class KannelSmsConfirmationDeliveryAdapterTest {

    @Test
    void normalizes_bank_phone_format() {
        assertThat(KannelSmsConfirmationDeliveryAdapter.toKannelPhone("6 99 12 34 56"))
                .isEqualTo("237699123456");
        assertThat(KannelSmsConfirmationDeliveryAdapter.toKannelPhone("+237699123456"))
                .isEqualTo("237699123456");
        assertThat(KannelSmsConfirmationDeliveryAdapter.toKannelPhone("237699123456"))
                .isEqualTo("237699123456");
    }

    @Test
    void sms_channel_can_be_disabled_without_kannel_credentials() {
        ConfirmationRecipientPort recipients = new ConfirmationRecipientPort() {
            @Override
            public Optional<String> findEmail(String financialInstitutionCode, String customerReference) {
                return Optional.empty();
            }

            @Override
            public Optional<String> findPhone(String financialInstitutionCode, String customerReference) {
                return Optional.of("699123456");
            }
        };

        var adapter = new KannelSmsConfirmationDeliveryAdapter(
                RestClient.create(),
                recipients,
                "",
                "",
                "",
                "LAREGIONALE",
                false
        );

        assertThat(adapter.enabledChannels()).isEmpty();
    }
}
