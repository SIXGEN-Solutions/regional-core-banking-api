package com.regional.corebanking.confirmation.application.port.out;

import com.regional.corebanking.confirmation.domain.ConfirmationChallenge;
import com.regional.corebanking.confirmation.domain.DeliveryChannel;
import java.util.Set;

public interface ConfirmationDeliveryPort {
    enum Outcome { DELIVERED, CONFIRMED_FAILURE, UNKNOWN }
    Set<DeliveryChannel> enabledChannels();
    Outcome dispatch(ConfirmationChallenge challenge, char[] otp);
}
