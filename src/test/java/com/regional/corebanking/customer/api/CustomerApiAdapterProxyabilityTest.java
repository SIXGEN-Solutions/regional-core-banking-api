package com.regional.corebanking.customer.api;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerApiAdapterProxyabilityTest {

    @Test
    void controllerMustRemainProxyableBySpring() {
        assertThat(Modifier.isFinal(CustomerApiAdapter.class.getModifiers()))
                .as("Spring may create a CGLIB proxy for the generated API controller")
                .isFalse();
    }
}
