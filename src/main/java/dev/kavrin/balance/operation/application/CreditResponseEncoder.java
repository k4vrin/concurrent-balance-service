package dev.kavrin.balance.operation.application;

public interface CreditResponseEncoder {
    StoredResponse encode(CreditCommand command, MutationResult result);
    void validate(MutationResult result);
}
