package dev.kavrin.balance.operation.web.error;

import dev.kavrin.balance.operation.application.result.OperationFailure;
import dev.kavrin.balance.operation.domain.RejectionReason;
import dev.kavrin.balance.operation.web.request.InvalidRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

// Media/method negotiation can fail before Spring selects a controller.
/**
 * Maps application and framework exceptions to stable HTTP problem responses.
 *
 * <p>Global advice also handles negotiation failures raised before controller selection. Technical
 * responses avoid leaking internal failures and preserve same-ID recovery guidance when a mutation's
 * commit may be uncertain; durable business decisions follow their separate retained-result path.
 */
@RestControllerAdvice
public final class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(InvalidRequest.class)
    ProblemDetail invalidRequest() {
        return ApiProblems.problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request fields are invalid.");
    }

    @ExceptionHandler(OperationFailure.class)
    ProblemDetail applicationFailure(OperationFailure failure) {
        return switch (failure.reason()) {
            case ACCOUNT_NOT_FOUND -> ApiProblems.rejection(RejectionReason.ACCOUNT_NOT_FOUND);
            case TRANSACTION_ID_CONFLICT -> ApiProblems.problem(HttpStatus.CONFLICT,
                    "TRANSACTION_ID_CONFLICT", "Transaction ID is already bound to a different request.");
            case UNSUPPORTED_CURRENCY -> ApiProblems.problem(HttpStatus.BAD_REQUEST,
                    "UNSUPPORTED_CURRENCY", "Currency is not supported for a new operation.");
            case UNAVAILABLE -> ApiProblems.problem(HttpStatus.SERVICE_UNAVAILABLE, "OPERATION_UNAVAILABLE",
                    "Operation outcome is unavailable. Retry mutations with the same transaction ID; commit may have occurred.");
        };
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpectedFailure(Exception failure) {
        return ApiProblems.problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Operation outcome is unknown. Retry mutations with the same transaction ID.");
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var code = switch (status.value()) {
            case 400 -> "INVALID_REQUEST";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 406 -> "NOT_ACCEPTABLE";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            default -> "HTTP_ERROR";
        };
        return super.handleExceptionInternal(exception, ApiProblems.problem(
                HttpStatus.valueOf(status.value()), code, "Request cannot be processed."), headers, status, request);
    }
}
