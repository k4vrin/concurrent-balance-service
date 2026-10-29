package dev.kavrin.balance.operation.web;

import dev.kavrin.balance.account.application.GetBalanceHandler;
import dev.kavrin.balance.operation.application.command.CreditCommand;
import dev.kavrin.balance.operation.application.command.DebitCommand;
import dev.kavrin.balance.operation.application.command.TransferCommand;
import dev.kavrin.balance.operation.application.credit.CreditHandler;
import dev.kavrin.balance.operation.application.debit.DebitHandler;
import dev.kavrin.balance.operation.application.result.OperationFailure;
import dev.kavrin.balance.operation.application.result.OperationFailureReason;
import dev.kavrin.balance.operation.application.transfer.TransferHandler;
import dev.kavrin.balance.operation.web.request.AmountRequest;
import dev.kavrin.balance.operation.web.request.RequestValues;
import dev.kavrin.balance.operation.web.request.TransferRequest;
import dev.kavrin.balance.operation.web.response.BalanceResponse;
import dev.kavrin.balance.operation.web.response.OperationResponseBody;
import dev.kavrin.balance.operation.web.response.OperationResponses;
import jakarta.validation.Valid;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Adapts the four public JSON routes to typed commands and application handlers.
 *
 * <p>Request parsing and validation precede application calls. Optional handlers represent deliberately
 * unavailable execution paths, which return unavailability. Transaction ownership, durable identity
 * and financial writes remain in the application/persistence flow rather than in this controller.
 */
@RestController
@RequestMapping("/api/v1")
public final class BalanceController {
    private final Optional<CreditHandler> credits;
    private final Optional<DebitHandler> debits;
    private final Optional<TransferHandler> transfers;
    private final Optional<GetBalanceHandler> balances;

    // Execution-disabled runtime omits mutation handlers; smoke assembly also omits balance persistence.
    public BalanceController(Optional<CreditHandler> credits, Optional<DebitHandler> debits,
            Optional<TransferHandler> transfers, Optional<GetBalanceHandler> balances) {
        this.credits = credits;
        this.debits = debits;
        this.transfers = transfers;
        this.balances = balances;
    }

    @PostMapping(path = "/accounts/{accountId}/credits", consumes = "application/json")
    public ResponseEntity<OperationResponseBody> credit(@PathVariable String accountId, @Valid @RequestBody AmountRequest request) {
        var command = new CreditCommand(RequestValues.transaction(request.transactionId()),
                RequestValues.account(accountId), RequestValues.money(request.amountRial(), request.currency()));
        return OperationResponses.from(credits.orElseThrow(BalanceController::unavailable).handle(command));
    }

    @PostMapping(path = "/accounts/{accountId}/debits", consumes = "application/json")
    public ResponseEntity<OperationResponseBody> debit(@PathVariable String accountId, @Valid @RequestBody AmountRequest request) {
        var command = new DebitCommand(RequestValues.transaction(request.transactionId()),
                RequestValues.account(accountId), RequestValues.money(request.amountRial(), request.currency()));
        return OperationResponses.from(debits.orElseThrow(BalanceController::unavailable).handle(command));
    }

    @PostMapping(path = "/transfers", consumes = "application/json")
    public ResponseEntity<OperationResponseBody> transfer(@Valid @RequestBody TransferRequest request) {
        var command = RequestValues.parse(() -> new TransferCommand(
                RequestValues.transaction(request.transactionId()), RequestValues.account(request.sourceAccountId()),
                RequestValues.account(request.destinationAccountId()),
                RequestValues.money(request.amountRial(), request.currency())));
        return OperationResponses.from(transfers.orElseThrow(BalanceController::unavailable).handle(command));
    }

    @GetMapping("/accounts/{accountId}/balance")
    public BalanceResponse balance(@PathVariable String accountId) {
        var id = RequestValues.account(accountId);
        var handler = balances.orElseThrow(BalanceController::unavailable);
        try {
            var balance = handler.handle(id);
            return new BalanceResponse(id.value().toString(), balance.amount(), balance.currency().getCurrencyCode());
        } catch (TransactionException failure) {
            throw new OperationFailure(OperationFailureReason.UNAVAILABLE, failure);
        }
    }

    private static OperationFailure unavailable() {
        return new OperationFailure(OperationFailureReason.UNAVAILABLE);
    }
}
