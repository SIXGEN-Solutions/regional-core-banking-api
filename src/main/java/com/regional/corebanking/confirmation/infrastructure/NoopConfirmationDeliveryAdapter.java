package com.regional.corebanking.confirmation.infrastructure;
import com.regional.corebanking.confirmation.application.port.out.ConfirmationDeliveryPort;
import com.regional.corebanking.confirmation.domain.*;
import java.util.Set;
public final class NoopConfirmationDeliveryAdapter implements ConfirmationDeliveryPort {
    private final Set<DeliveryChannel> channels;
    public NoopConfirmationDeliveryAdapter(Set<DeliveryChannel> channels){ this.channels=Set.copyOf(channels); }
    public Set<DeliveryChannel> enabledChannels(){ return channels; }
    public void dispatch(ConfirmationChallenge challenge,char[] otp){
        // R5.1 boundary only: no BKSMS/SMTP behavior without approved infrastructure evidence.
    }
}
