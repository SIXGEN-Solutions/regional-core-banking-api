package com.regional.corebanking.payment.api;

import com.regional.corebanking.payment.application.exception.PaymentExecutionIdempotencyConflictException;
import com.regional.corebanking.payment.application.exception.PaymentExecutionNotFoundException;
import com.regional.corebanking.payment.application.exception.PaymentExecutionUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class PaymentExecutionApiExceptionHandler {

    @ExceptionHandler(PaymentExecutionNotFoundException.class)
    ProblemDetail notFound(PaymentExecutionNotFoundException exception, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Payment execution result not found",
                exception.getMessage(), "PAYMENT_EXECUTION_NOT_FOUND", request);
    }

    @ExceptionHandler(PaymentExecutionIdempotencyConflictException.class)
    ProblemDetail conflict(PaymentExecutionIdempotencyConflictException exception, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Payment execution idempotency conflict",
                exception.getMessage(), "IDEMPOTENCY_CONFLICT", request);
    }

    @ExceptionHandler(PaymentExecutionUnavailableException.class)
    ProblemDetail unavailable(PaymentExecutionUnavailableException exception, HttpServletRequest request) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Payment execution unavailable",
                exception.getMessage(), "PAYMENT_EXECUTION_UNAVAILABLE", request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalid(IllegalArgumentException exception, HttpServletRequest request) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Payment execution request rejected",
                exception.getMessage(), "PAYMENT_EXECUTION_REJECTED", request);
    }

    private static ProblemDetail problem(
            HttpStatus status,
            String title,
            String detail,
            String code,
            HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("code", code);
        String correlationId = request.getHeader("X-Correlation-ID");
        if (correlationId != null && !correlationId.isBlank()) {
            problem.setProperty("correlationId", correlationId);
        }
        return problem;
    }
}
