package dev.kavrin.balance.operation.web;

import dev.kavrin.balance.operation.application.BalanceOperations;
import dev.kavrin.balance.operation.application.command.CreditCommand;
import dev.kavrin.balance.operation.application.command.DebitCommand;
import dev.kavrin.balance.operation.application.command.TransferCommand;
import dev.kavrin.balance.operation.web.request.AmountRequest;
import dev.kavrin.balance.operation.web.request.RequestValues;
import dev.kavrin.balance.operation.web.request.TransferRequest;
import dev.kavrin.balance.operation.web.response.BalanceResponse;
import dev.kavrin.balance.operation.web.response.OperationResponseBody;
import dev.kavrin.balance.operation.web.response.OperationResponses;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public final class BalanceController {
    private final BalanceOperations operations;

    public BalanceController(BalanceOperations operations) {
        this.operations = operations;
    }

    @PostMapping(path = "/accounts/{accountId}/credits", consumes = "application/json")
    public ResponseEntity<OperationResponseBody> credit(@PathVariable String accountId, @Valid @RequestBody AmountRequest request) {
        var command = new CreditCommand(RequestValues.transaction(request.transactionId()),
                RequestValues.account(accountId), RequestValues.money(request.amountRial(), request.currency()));
        return OperationResponses.from(operations.credit(command));
    }

    @PostMapping(path = "/accounts/{accountId}/debits", consumes = "application/json")
    public ResponseEntity<OperationResponseBody> debit(@PathVariable String accountId, @Valid @RequestBody AmountRequest request) {
        var command = new DebitCommand(RequestValues.transaction(request.transactionId()),
                RequestValues.account(accountId), RequestValues.money(request.amountRial(), request.currency()));
        return OperationResponses.from(operations.debit(command));
    }

    @PostMapping(path = "/transfers", consumes = "application/json")
    public ResponseEntity<OperationResponseBody> transfer(@Valid @RequestBody TransferRequest request) {
        var command = RequestValues.parse(() -> new TransferCommand(
                RequestValues.transaction(request.transactionId()), RequestValues.account(request.sourceAccountId()),
                RequestValues.account(request.destinationAccountId()),
                RequestValues.money(request.amountRial(), request.currency())));
        return OperationResponses.from(operations.transfer(command));
    }

    @GetMapping("/accounts/{accountId}/balance")
    public BalanceResponse balance(@PathVariable String accountId) {
        var id = RequestValues.account(accountId);
        var balance = operations.balance(id);
        return new BalanceResponse(id.value().toString(), balance.amount(), balance.currency().getCurrencyCode());
    }
}
