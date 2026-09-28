package com.regional.corebanking.confirmation.infrastructure;
import com.regional.corebanking.confirmation.application.port.out.ConfirmationDeliveryPort;
import com.regional.corebanking.confirmation.domain.DeliveryChannel;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
class NoopConfirmationDeliveryAdapterTest {
 @Test void noopNeverClaimsDeliveryAccepted(){
  var a=new NoopConfirmationDeliveryAdapter(Set.of(DeliveryChannel.EMAIL));
  assertThat(a.dispatch("REGIONAL",null,new char[0])).isEqualTo(ConfirmationDeliveryPort.Outcome.UNKNOWN);
 }
}
