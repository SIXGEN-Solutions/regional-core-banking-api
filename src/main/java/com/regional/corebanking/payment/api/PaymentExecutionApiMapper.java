package com.regional.corebanking.payment.api;

import com.regional.corebanking.generated.model.PaymentEventEnvelope;
import com.regional.corebanking.generated.model.PaymentEventResult;
import com.regional.corebanking.generated.model.PaymentExecutionCheck;
import com.regional.corebanking.generated.model.PaymentProviderEvent;
import com.regional.corebanking.payment.domain.PaymentExecutionCommand;
import com.regional.corebanking.payment.domain.BkeveEvent;
import org.springframework.beans.BeanWrapperImpl;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PaymentExecutionApiMapper {

    public PaymentExecutionCommand toDomain(PaymentEventEnvelope source) {
        return new PaymentExecutionCommand(
                source.getPaymentReference(),
                source.getSnapshotVersion(),
                toDomain(source.getProviderEvent()),
                source.getRequestedAt());
    }

    public PaymentEventResult toApi(com.regional.corebanking.payment.domain.PaymentExecutionResult source) {
        PaymentEventResult target = new PaymentEventResult(
                source.paymentReference(),
                PaymentEventResult.OutcomeEnum.fromValue(source.outcome().name()),
                source.checks().stream().map(this::toApi).toList(),
                source.observedAt());
        target.setBankReference(source.bankReference());
        target.setReasonCode(source.reasonCode());
        return target;
    }

    private com.regional.corebanking.payment.domain.PaymentProviderEvent toDomain(PaymentProviderEvent source) {
        if (source == null) throw new IllegalArgumentException("providerEvent is required");
        var dto = new BeanWrapperImpl(source);
        Map<String, Object> supplied = new LinkedHashMap<>();
        for (String column : BkeveEvent.COLUMNS) {
            String field = column.toLowerCase(java.util.Locale.ROOT);
            if (!dto.isReadableProperty(field)) {
                throw new IllegalStateException("Generated BKEVE HTTP model missing property: " + field);
            }
            Object value = dto.getPropertyValue(field);
            if (value != null) supplied.put(column, value);
        }
        var bankingEvent = new BkeveEvent(supplied);
        var operationCode = first(source.getOperationCode(), (String) supplied.get("OPE"));
        var currency = source.getCurrency() != null ? source.getCurrency() : (String) supplied.get("DEV");
        var nature = first(source.getNature(), (String) supplied.get("NAT"));
        var technicalUser = first(source.getTechnicalUser(), (String) supplied.get("UTI"));
        var debtorRef = first(source.getDebtorAccountReference(), account(supplied, "1"));
        var creditorRef = first(source.getCreditorAccountReference(), account(supplied, "2"));
        var debitAmount = first(source.getDebitAmount(), number(supplied.get("MON1")));
        var creditAmount = first(source.getCreditAmount(), number(supplied.get("MON2")));
        var label = first(source.getLabel(), (String) supplied.get("LIB1"));
        return new com.regional.corebanking.payment.domain.PaymentProviderEvent(
                source.getPaymentReference(), operationCode, currency, nature, technicalUser,
                debtorRef, creditorRef, debitAmount, creditAmount, label,
                first(source.getAgsa(), (String) supplied.get("AGSA")),
                first(source.getAge(), (String) supplied.get("AGE")),
                first(source.getCsp1(), (String) supplied.get("CSP1")), bankingEvent);
    }

    private static <T> T first(T direct, T bank) {
        if (direct != null && bank != null) {
            boolean same = direct instanceof java.math.BigDecimal a && bank instanceof java.math.BigDecimal b
                    ? a.compareTo(b) == 0 : direct.equals(bank);
            if (!same) throw new IllegalArgumentException("Conflicting payment aliases and BKEVE field");
        }
        return direct != null ? direct : bank;
    }

    private static java.math.BigDecimal number(Object value) {
        if (value == null) return null;
        if (value instanceof java.math.BigDecimal n) return n;
        if (value instanceof Number n) return new java.math.BigDecimal(n.toString());
        throw new IllegalArgumentException("BKEVE amount is not numeric");
    }

    private static String account(Map<String, Object> bank, String suffix) {
        Object age = bank.get("AGE" + suffix);
        Object ncp = bank.get("NCP" + suffix);
        Object clc = bank.get("CLC" + suffix);
        return age == null || ncp == null || clc == null ? null : age + "-" + ncp + "-" + clc;
    }

    private PaymentExecutionCheck toApi(com.regional.corebanking.payment.domain.PaymentExecutionCheck source) {
        PaymentExecutionCheck target = new PaymentExecutionCheck(
                PaymentExecutionCheck.TypeEnum.fromValue(source.type().name()),
                PaymentExecutionCheck.ResultEnum.fromValue(source.result().name()));
        target.setReasonCode(source.reasonCode());
        return target;
    }
}
