package dev.kavrin.balance.operation.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import dev.kavrin.balance.operation.domain.RejectionReason;

final class ApiProblems {
    private ApiProblems() { }

    static ProblemDetail problem(HttpStatus status, String code, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }

    static ProblemDetail rejection(RejectionReason reason) {
        return switch (reason) {
            case ACCOUNT_NOT_FOUND -> problem(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "Requested account was not found.");
            case INSUFFICIENT_FUNDS -> problem(HttpStatus.CONFLICT, "INSUFFICIENT_FUNDS", "Available funds are insufficient.");
            case BALANCE_OVERFLOW -> problem(HttpStatus.CONFLICT, "BALANCE_OVERFLOW", "Resulting balance exceeds the supported range.");
        };
    }
}
