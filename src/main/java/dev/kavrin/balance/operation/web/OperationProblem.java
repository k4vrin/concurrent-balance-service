package dev.kavrin.balance.operation.web;

import org.springframework.http.ProblemDetail;

/** Keeps terminal rejections in the typed response family while preserving ProblemDetail serialization. */
public final class OperationProblem extends ProblemDetail implements OperationResponseBody {
    OperationProblem(ProblemDetail problem) {
        super(problem);
    }
}
