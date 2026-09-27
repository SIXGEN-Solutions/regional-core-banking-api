package com.regional.corebanking.confirmation.application.port.out;
import com.regional.corebanking.confirmation.domain.*;
import java.util.Set;
public interface ConfirmationDeliveryPort {
    Set<DeliveryChannel> enabledChannels();
    void dispatch(ConfirmationChallenge challenge,char[] otp);
}
