package com.regional.corebanking.confirmation.application.port.in;
import com.regional.corebanking.confirmation.domain.*;
public interface PaymentConfirmationUseCase {
    ConfirmationChallenge create(String institution,String key,ConfirmationCommand.Create command);
    ConfirmationChallenge verify(String institution,String key,String reference,ConfirmationCommand.Verify command);
    ConfirmationChallenge replace(String institution,String key,String reference,ConfirmationCommand.Replace command);
    ConfirmationChallenge get(String institution,String reference);
    ConfirmationChallenge recover(String institution,String key);
    ConfirmationChallenge revoke(String institution,String key,String reference,ConfirmationCommand.Revoke command);
}
