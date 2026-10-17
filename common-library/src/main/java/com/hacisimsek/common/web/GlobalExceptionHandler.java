package com.hacisimsek.common.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns every error into an RFC 9457 {@code application/problem+json} response. Spring MVC's own
 * exceptions (bad JSON, wrong method, unknown path, ...) are handled by the base class.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String TYPE_PREFIX = "https://api.ecommerce.local/problems/";

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "not-found", "Resource not found", ex.getMessage());
    }

    @ExceptionHandler(BusinessRuleException.class)
    ProblemDetail handleBusinessRule(BusinessRuleException ex) {
        return problem(HttpStatus.CONFLICT, "business-rule", "Business rule violated", ex.getMessage());
    }

    @ExceptionHandler(DependencyUnavailableException.class)
    ProblemDetail handleDependencyUnavailable(DependencyUnavailableException ex) {
        log.warn("{}: {}", ex.getMessage(), ex.getCause() == null ? "" : ex.getCause().toString());
        ProblemDetail problem = problem(HttpStatus.SERVICE_UNAVAILABLE, "dependency-unavailable",
                "Service temporarily unavailable", ex.getMessage());
        problem.setProperty("retryable", true);
        return problem;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        // Never leak internals to the client
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Internal server error",
                "An unexpected error occurred");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        List<Map<String, Object>> errors = ex.getFieldErrors().stream()
                .map(e -> fieldError(e.getField(), e.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(validationProblem(errors));
    }

    /**
     * Raised instead of the above when a handler also validates plain parameters (e.g. a header with
     * {@code @Size}); reported in the same shape so clients see one validation format.
     */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                            HttpHeaders headers,
                                                                            HttpStatusCode status,
                                                                            WebRequest request) {
        List<Map<String, Object>> errors = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            if (result instanceof ParameterErrors parameterErrors) {
                parameterErrors.getFieldErrors()
                        .forEach(e -> errors.add(fieldError(e.getField(), e.getDefaultMessage())));
            } else {
                String name = result.getMethodParameter().getParameterName();
                result.getResolvableErrors().forEach(e -> errors.add(fieldError(name, e.getDefaultMessage())));
            }
        }
        return ResponseEntity.badRequest().body(validationProblem(errors));
    }

    private static ProblemDetail validationProblem(List<Map<String, Object>> errors) {
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "validation", "Validation failed",
                "Request has " + errors.size() + " invalid field(s)");
        body.setProperty("errors", errors);
        return body;
    }

    private static Map<String, Object> fieldError(String field, String message) {
        return Map.of("field", String.valueOf(field), "message", String.valueOf(message));
    }

    private static ProblemDetail problem(HttpStatus status, String type, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_PREFIX + type));
        problem.setTitle(title);
        return problem;
    }
}
