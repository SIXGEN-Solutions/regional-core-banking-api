package com.regional.corebanking.payment.api;

import com.regional.corebanking.generated.model.PaymentEventEnvelope;
import com.regional.corebanking.generated.model.PaymentEventResult;
import com.regional.corebanking.generated.model.PaymentExecutionCheck;
import com.regional.corebanking.generated.model.PaymentProviderEvent;
import com.regional.corebanking.payment.domain.PaymentExecutionCommand;
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
        return new com.regional.corebanking.payment.domain.PaymentProviderEvent(
                source.getPaymentReference(),
                source.getOperationCode(),
                source.getCurrency(),
                source.getNature(),
                source.getTechnicalUser(),
                source.getDebtorAccountReference(),
                source.getCreditorAccountReference(),
                source.getAmount(),
                source.getLabel());
    }

    private PaymentExecutionCheck toApi(com.regional.corebanking.payment.domain.PaymentExecutionCheck source) {
        PaymentExecutionCheck target = new PaymentExecutionCheck(
                PaymentExecutionCheck.TypeEnum.fromValue(source.type().name()),
                PaymentExecutionCheck.ResultEnum.fromValue(source.result().name()));
        target.setReasonCode(source.reasonCode());
        return target;
    }
}
