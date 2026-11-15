package com.regional.corebanking.confirmation.domain;
import java.math.BigDecimal;
public final class ConfirmationCommand {
    private ConfirmationCommand() {}
    public record Create(String paymentReference,String customerReference,String debtorAccountReference,
                         BigDecimal amount,String currency) {}
    public record Verify(String paymentReference,char[] otp) {}
    public record Replace(String paymentReference) {}
    public record Revoke(String paymentReference,String reasonCode) {}
}
