package com.regional.corebanking.customer.api;

import com.regional.corebanking.customer.application.exception.BankingAccessException;
import com.regional.corebanking.customer.application.exception.CustomerNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class CustomerApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request",
                exception.getMessage(), "ERROR", request);
    }

    @ExceptionHandler(CustomerNotFoundException.class)
    ProblemDetail notFound(CustomerNotFoundException exception, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Customer not found",
                exception.getMessage(), "ERROR", request);
    }

    @ExceptionHandler(BankingAccessException.class)
    ProblemDetail bankingUnavailable(BankingAccessException exception, HttpServletRequest request) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Core Banking unavailable",
                exception.getMessage(), exception.code().name(), request);
    }

    private static ProblemDetail problem(
            HttpStatus status,
            String title,
            String detail,
            String code,
            HttpServletRequest request
    ) {
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
