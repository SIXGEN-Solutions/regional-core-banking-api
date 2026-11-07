package com.regional.corebanking.payment.api;

import com.regional.corebanking.payment.application.exception.PaymentExecutionNotFoundException;
import com.regional.corebanking.payment.application.exception.PaymentExecutionUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

class PaymentExecutionLookupHttpSemanticsTest {
    private final PaymentExecutionApiExceptionHandler handler = new PaymentExecutionApiExceptionHandler();

    @Test
    void unknownPaymentReferenceGives404WithExplicitUnknownOutcome() {
        var body = handler.notFound(new PaymentExecutionNotFoundException("not found", "PAY-404"));
        assertEquals(HttpStatus.NOT_FOUND, body.getStatusCode());
        assertEquals("PAY-404", body.getBody().get("paymentReference"));
        assertEquals("UNKNOWN", body.getBody().get("outcome"));
        assertEquals(java.util.List.of(), body.getBody().get("checks"));
        assertNull(body.getBody().get("reasonCode"));
        assertNotNull(body.getBody().get("observedAt"));
    }

    @Test
    void unknownIdempotencyKeyGives404WithoutFabricatedReference() {
        var body = handler.notFound(new PaymentExecutionNotFoundException("not found"));
        assertEquals(HttpStatus.NOT_FOUND, body.getStatusCode());
        assertEquals("UNKNOWN", body.getBody().get("outcome"));
        assertFalse(body.getBody().containsKey("paymentReference"));
        assertEquals(java.util.List.of(), body.getBody().get("checks"));
    }

    @Test
    void paymentReferenceGetExposes404UnknownThroughSpringMvc() throws Exception {
        var useCase = org.mockito.Mockito.mock(
                com.regional.corebanking.payment.application.port.in.PaymentExecutionUseCase.class);
        org.mockito.Mockito.when(useCase.recoverByPaymentReference("REGIONAL", "PAY-404"))
                .thenThrow(new PaymentExecutionNotFoundException("not found", "PAY-404"));
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                new PaymentExecutionApiAdapter(useCase, new PaymentExecutionApiMapper()))
                .setControllerAdvice(new PaymentExecutionApiExceptionHandler()).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/payment-events/PAY-404")
                        .header("X-Correlation-ID", "11111111-1111-1111-1111-111111111111")
                        .header("X-Financial-Institution-Code", "REGIONAL"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.outcome")
                        .value("UNKNOWN"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.paymentReference")
                        .value("PAY-404"));
        org.mockito.Mockito.verify(useCase, org.mockito.Mockito.never()).execute(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void idempotencyGetExposes404UnknownWithoutInventingReference() throws Exception {
        var useCase = org.mockito.Mockito.mock(
                com.regional.corebanking.payment.application.port.in.PaymentExecutionUseCase.class);
        org.mockito.Mockito.when(useCase.recoverByIdempotencyKey("REGIONAL", "IDEM-404"))
                .thenThrow(new PaymentExecutionNotFoundException("not found"));
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                new PaymentExecutionApiAdapter(useCase, new PaymentExecutionApiMapper()))
                .setControllerAdvice(new PaymentExecutionApiExceptionHandler()).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/payment-events/idempotency/IDEM-404")
                        .header("X-Correlation-ID", "11111111-1111-1111-1111-111111111111")
                        .header("X-Financial-Institution-Code", "REGIONAL"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.outcome")
                        .value("UNKNOWN"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.paymentReference")
                        .doesNotExist());
        org.mockito.Mockito.verify(useCase, org.mockito.Mockito.never()).execute(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void bankingUnavailableRemains503TechnicalProblem() {
        var problem = handler.unavailable(new PaymentExecutionUnavailableException("bank unavailable"),
                new MockHttpServletRequest());
        assertEquals(503, problem.getStatus());
        assertEquals("PAYMENT_EXECUTION_UNAVAILABLE", problem.getProperties().get("code"));
    }
}
