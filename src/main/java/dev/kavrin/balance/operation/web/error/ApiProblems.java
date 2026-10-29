package dev.kavrin.balance.operation.web.error;

import dev.kavrin.balance.operation.domain.RejectionReason;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * Builds consistent ProblemDetail responses with stable application error codes.
 *
 * <p>Business rejection reasons map to explicit status/code pairs. Creating a problem response does
 * not reserve an operation or determine whether a decision was durably committed.
 */
public final class ApiProblems {
    private ApiProblems() { }

    public static ProblemDetail problem(HttpStatus status, String code, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }

    public static ProblemDetail rejection(RejectionReason reason) {
        return switch (reason) {
            case ACCOUNT_NOT_FOUND -> problem(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "Requested account was not found.");
            case INSUFFICIENT_FUNDS -> problem(HttpStatus.CONFLICT, "INSUFFICIENT_FUNDS", "Available funds are insufficient.");
            case BALANCE_OVERFLOW -> problem(HttpStatus.CONFLICT, "BALANCE_OVERFLOW", "Resulting balance exceeds the supported range.");
            case TRANSACTION_LIMIT_EXCEEDED -> problem(HttpStatus.CONFLICT, "TRANSACTION_LIMIT_EXCEEDED", "Amount exceeds the transaction limit.");
            case BALANCE_LIMIT_EXCEEDED -> problem(HttpStatus.CONFLICT, "BALANCE_LIMIT_EXCEEDED", "Resulting customer balance exceeds the limit.");
        };
    }
}
