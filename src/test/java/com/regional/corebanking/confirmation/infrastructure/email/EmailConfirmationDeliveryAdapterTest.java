package com.regional.corebanking.confirmation.infrastructure.email;

import com.regional.corebanking.confirmation.application.port.out.*;
import com.regional.corebanking.confirmation.domain.*;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmailConfirmationDeliveryAdapterTest {
    private ConfirmationChallenge challenge() {
        return new ConfirmationChallenge("CHL-1","PAY-0123456789ABCDEFGHJKMNPQRS","CUS-1","001-123456-7",
                new BigDecimal("1000"),"XAF","verifier","v1",ChallengeStatus.ACTIVE,
                ConfirmationBusinessCode.CHALLENGE_ACTIVE,0,0,Set.of(DeliveryChannel.EMAIL),
                Instant.now(),Instant.now().plusSeconds(300),null,null,null);
    }
    private JavaMailSender sender() {
        JavaMailSender s=mock(JavaMailSender.class);
        when(s.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        return s;
    }

    @Test void sendsToBankResolvedEmail() {
        JavaMailSender s=sender();
        var a=new EmailConfirmationDeliveryAdapter(s,(i,c)->Optional.of("client@regional.test"),
                "no-reply@regional.test",true);
        assertThat(a.dispatch("REGIONAL",challenge(),"123456".toCharArray())).isEqualTo(ConfirmationDeliveryPort.Outcome.ACCEPTED);
        verify(s).send(any(MimeMessage.class));
    }

    @Test void missingBankEmailIsConfirmedFailure() {
        JavaMailSender s=sender();
        var a=new EmailConfirmationDeliveryAdapter(s,(i,c)->Optional.empty(),"no-reply@regional.test",true);
        assertThat(a.dispatch("REGIONAL",challenge(),"123456".toCharArray())).isEqualTo(ConfirmationDeliveryPort.Outcome.CONFIRMED_FAILURE);
        verify(s,never()).send(any(MimeMessage.class));
    }

    @Test void disabledConfigurationDoesNotSend() {
        JavaMailSender s=sender();
        var a=new EmailConfirmationDeliveryAdapter(s,(i,c)->Optional.of("client@regional.test"),
                "no-reply@regional.test",false);
        assertThat(a.enabledChannels()).isEmpty();
        assertThatThrownBy(()->a.dispatch("REGIONAL",challenge(),"123456".toCharArray()))
                .isInstanceOf(IllegalStateException.class);
        verify(s,never()).send(any(MimeMessage.class));
    }

    @Test void smtpTimeoutIsUnknownOutcome() {
        JavaMailSender s=sender();
        doThrow(new MailSendException("SMTP timeout",new SocketTimeoutException("timeout")))
                .when(s).send(any(MimeMessage.class));
        var a=new EmailConfirmationDeliveryAdapter(s,(i,c)->Optional.of("client@regional.test"),
                "no-reply@regional.test",true);
        assertThat(a.dispatch("REGIONAL",challenge(),"123456".toCharArray())).isEqualTo(ConfirmationDeliveryPort.Outcome.UNKNOWN);
    }

    @Test void confirmedSmtpErrorIsConfirmedFailure() {
        JavaMailSender s=sender();
        doThrow(new MailSendException("SMTP rejected")).when(s).send(any(MimeMessage.class));
        var a=new EmailConfirmationDeliveryAdapter(s,(i,c)->Optional.of("client@regional.test"),
                "no-reply@regional.test",true);
        assertThat(a.dispatch("REGIONAL",challenge(),"123456".toCharArray())).isEqualTo(ConfirmationDeliveryPort.Outcome.CONFIRMED_FAILURE);
    }
}
