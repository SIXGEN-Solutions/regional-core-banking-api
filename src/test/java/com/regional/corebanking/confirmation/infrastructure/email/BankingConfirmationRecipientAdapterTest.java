package com.regional.corebanking.confirmation.infrastructure.email;

import com.regional.corebanking.customer.application.port.out.CustomerBankingPort;
import com.regional.corebanking.customer.domain.CustomerIdentity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class BankingConfirmationRecipientAdapterTest {
    @Test
    void resolvesEmailFromExistingCustomerBankingPort() {
        CustomerBankingPort customers = mock(CustomerBankingPort.class);
        when(customers.getCustomer("REGIONAL", "CUS-001"))
                .thenReturn(customer("CUS-001", " client@regional.test "));
        var adapter = new BankingConfirmationRecipientAdapter(customers);
        assertThat(adapter.findEmail("REGIONAL", "CUS-001")).contains("client@regional.test");
        verify(customers).getCustomer("REGIONAL", "CUS-001");
    }

    @Test
    void returnsEmptyWhenCustomerHasNoEmail() {
        CustomerBankingPort customers = mock(CustomerBankingPort.class);
        when(customers.getCustomer("REGIONAL", "CUS-002")).thenReturn(customer("CUS-002", null));
        assertThat(new BankingConfirmationRecipientAdapter(customers)
                .findEmail("REGIONAL", "CUS-002")).isEmpty();
    }

    @Test
    void returnsEmptyWhenCustomerIsNotFound() {
        CustomerBankingPort customers = mock(CustomerBankingPort.class);
        when(customers.getCustomer("REGIONAL", "CUS-404")).thenReturn(null);
        assertThat(new BankingConfirmationRecipientAdapter(customers)
                .findEmail("REGIONAL", "CUS-404")).isEmpty();
    }

    private static CustomerIdentity customer(String reference, String email) {
        return new CustomerIdentity(reference, reference, "REGIONAL", null, "Customer", null,
                email, CustomerIdentity.KycStatus.UNKNOWN, List.of(), null,
                Instant.parse("2026-09-28T20:00:00Z"));
    }
}
