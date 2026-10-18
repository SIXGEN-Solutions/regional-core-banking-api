package com.regional.corebanking.payment.application.service;

import com.regional.corebanking.payment.domain.PaymentExecutionCommand;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class PaymentExecutionFingerprint {

    private PaymentExecutionFingerprint() {
    }

    static String sha256(PaymentExecutionCommand command) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            add(digest, command.paymentReference());
            add(digest, command.snapshotVersion());
            var event = command.providerEvent();
            add(digest, event.paymentReference());
            add(digest, event.operationCode());
            add(digest, event.currency());
            add(digest, event.nature());
            add(digest, event.technicalUser());
            add(digest, event.debtorAccountReference());
            add(digest, event.creditorAccountReference());
            add(digest, normalize(event.debitAmount()));
            add(digest, normalize(event.creditAmount()));
            add(digest, event.label());
            add(digest, event.agsa());
            add(digest, event.age());
            add(digest, event.csp1());
            // Append only supplied BKEVE properties; the existing fingerprint of
            // a 13-property payment remains stable.
            event.bkeveEvent().values().entrySet().stream()
                    .sorted(java.util.Map.Entry.comparingByKey())
                    .filter(entry -> !((entry.getKey().equals("AGSA") && java.util.Objects.equals(entry.getValue(), event.agsa()))
                            || (entry.getKey().equals("AGE") && java.util.Objects.equals(entry.getValue(), event.age()))
                            || (entry.getKey().equals("CSP1") && java.util.Objects.equals(entry.getValue(), event.csp1()))))
                    .forEach(entry -> {
                        add(digest, entry.getKey());
                        Object value = entry.getValue();
                        if (value instanceof BigDecimal decimal) add(digest, normalize(decimal));
                        else add(digest, value == null ? null : value.toString());
                    });
            add(digest, command.requestedAt() == null ? null : command.requestedAt().toInstant().toString());
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static String normalize(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private static void add(MessageDigest digest, String value) {
        byte[] bytes = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
        digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII));
        digest.update((byte) ':');
        digest.update(bytes);
        digest.update((byte) '|');
    }
}
