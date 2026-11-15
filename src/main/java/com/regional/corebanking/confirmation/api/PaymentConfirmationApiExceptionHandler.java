package com.regional.corebanking.confirmation.api;

import com.regional.corebanking.confirmation.application.exception.ChallengeNotFoundException;
import com.regional.corebanking.confirmation.application.exception.ConfirmationRejectedException;
import com.regional.corebanking.confirmation.application.exception.IdempotencyConflictException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class PaymentConfirmationApiExceptionHandler {
    @ExceptionHandler(ChallengeNotFoundException.class)
    ProblemDetail notFound(ChallengeNotFoundException exception, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Payment confirmation challenge not found",
                exception.getMessage(), "CHALLENGE_NOT_FOUND", request);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    ProblemDetail conflict(IdempotencyConflictException exception, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Idempotency conflict",
                exception.getMessage(), "IDEMPOTENCY_CONFLICT", request);
    }

    @ExceptionHandler({ConfirmationRejectedException.class, IllegalArgumentException.class})
    ProblemDetail unprocessable(RuntimeException exception, HttpServletRequest request) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Confirmation request rejected",
                exception.getMessage(), "CONFIRMATION_REJECTED", request);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail,
                                         String code, HttpServletRequest request) {
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
