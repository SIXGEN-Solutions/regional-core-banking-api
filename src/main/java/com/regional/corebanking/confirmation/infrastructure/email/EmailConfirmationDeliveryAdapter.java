package com.regional.corebanking.confirmation.infrastructure.email;

import com.regional.corebanking.confirmation.application.port.out.ConfirmationDeliveryPort;
import com.regional.corebanking.confirmation.application.port.out.ConfirmationRecipientPort;
import com.regional.corebanking.confirmation.domain.ConfirmationChallenge;
import com.regional.corebanking.confirmation.domain.DeliveryChannel;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Optional;
import java.util.Set;

public final class EmailConfirmationDeliveryAdapter implements ConfirmationDeliveryPort {
    private final JavaMailSender sender;
    private final ConfirmationRecipientPort recipients;
    private final String from;
    private final boolean enabled;

    public EmailConfirmationDeliveryAdapter(JavaMailSender sender, ConfirmationRecipientPort recipients,
                                            String from, boolean enabled) {
        this.sender=sender; this.recipients=recipients; this.from=from; this.enabled=enabled;
        if(enabled && (from==null || from.isBlank())) throw new IllegalArgumentException("EMAIL sender is required");
    }

    public Set<DeliveryChannel> enabledChannels() {
        return enabled ? Set.of(DeliveryChannel.EMAIL) : Set.of();
    }

    public Outcome dispatch(String institution, ConfirmationChallenge challenge, char[] otp) {
        if(!enabled) throw new IllegalStateException("EMAIL OTP delivery is disabled");
        Optional<String> address=recipients.findEmail(institution,challenge.customerReference());
        if(address.isEmpty() || address.get().isBlank()) return Outcome.CONFIRMED_FAILURE;
        try {
            MimeMessage m=sender.createMimeMessage();
            m.setFrom(from);
            m.setRecipients(jakarta.mail.Message.RecipientType.TO,address.get());
            m.setSubject("Payment confirmation");
            m.setText("Confirmation code: "+new String(otp),"UTF-8");
            sender.send(m);
            return Outcome.DELIVERED;
        } catch (MailException | MessagingException e) {
            return timeout(e) ? Outcome.UNKNOWN : Outcome.CONFIRMED_FAILURE;
        }
    }

    private static boolean timeout(Throwable e) {
        for(Throwable t=e;t!=null;t=t.getCause())
            if(t instanceof SocketTimeoutException || t instanceof ConnectException) return true;
        return false;
    }
}
