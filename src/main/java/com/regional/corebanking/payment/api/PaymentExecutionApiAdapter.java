package com.regional.corebanking.payment.api;

import com.regional.corebanking.generated.api.PaymentExecutionApi;
import com.regional.corebanking.generated.model.PaymentEventEnvelope;
import com.regional.corebanking.generated.model.PaymentEventResult;
import com.regional.corebanking.payment.application.port.in.PaymentExecutionUseCase;
import com.regional.corebanking.payment.domain.PaymentExecutionOutcome;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class PaymentExecutionApiAdapter implements PaymentExecutionApi {

    private final PaymentExecutionUseCase useCase;
    private final PaymentExecutionApiMapper mapper;

    public PaymentExecutionApiAdapter(PaymentExecutionUseCase useCase, PaymentExecutionApiMapper mapper) {
        this.useCase = useCase;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<PaymentEventResult> executePaymentEvent(
            UUID xCorrelationID,
            String xFinancialInstitutionCode,
            String idempotencyKey,
            PaymentEventEnvelope request) {
        var result = useCase.execute(
                xFinancialInstitutionCode,
                idempotencyKey,
                mapper.toDomain(request));
        var body = mapper.toApi(result);
        return result.outcome() == PaymentExecutionOutcome.UNKNOWN
                ? ResponseEntity.accepted().body(body)
                : ResponseEntity.ok(body);
    }

    @Override
    public ResponseEntity<PaymentEventResult> getPaymentEventByPaymentReference(
            UUID xCorrelationID,
            String xFinancialInstitutionCode,
            String paymentReference) {
        return ResponseEntity.ok(mapper.toApi(
                useCase.recoverByPaymentReference(xFinancialInstitutionCode, paymentReference)));
    }

    @Override
    public ResponseEntity<PaymentEventResult> getPaymentEventByIdempotencyKey(
            UUID xCorrelationID,
            String xFinancialInstitutionCode,
            String idempotencyKey) {
        return ResponseEntity.ok(mapper.toApi(
                useCase.recoverByIdempotencyKey(xFinancialInstitutionCode, idempotencyKey)));
    }
}
