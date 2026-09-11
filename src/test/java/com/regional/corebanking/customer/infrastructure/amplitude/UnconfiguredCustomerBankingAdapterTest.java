package com.regional.corebanking.customer.infrastructure.amplitude;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnconfiguredCustomerBankingAdapterTest {

    @Test
    void failsClosedUntilBankEvidenceIsAvailable() {
        var adapter = new UnconfiguredCustomerBankingAdapter();

        assertThatThrownBy(() ->
                adapter.searchCustomers("REGIONAL", "NIU-001", null))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("approved La Régionale");
    }
}
