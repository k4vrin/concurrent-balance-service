package dev.kavrin.balance.account.application;

import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.money.Money;
import dev.kavrin.balance.operation.application.result.OperationFailure;
import dev.kavrin.balance.operation.application.result.OperationFailureReason;
import jakarta.persistence.PersistenceException;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Returns an account's authoritative projection within a read-only READ_COMMITTED transaction.
 *
 * <p>Missing accounts and unavailable persistence remain distinct application failures. The query
 * creates no operation, journal or posting, and its transaction boundary is supplied by Spring's proxy.
 */
@Service
public class GetBalanceHandler {
    private final BalanceReader balances;

    public GetBalanceHandler(BalanceReader balances) { this.balances = balances; }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 5)
    public Money handle(AccountId id) {
        try {
            return balances.findBalance(id).orElseThrow(() ->
                    new OperationFailure(OperationFailureReason.ACCOUNT_NOT_FOUND));
        } catch (DataAccessException | PersistenceException unavailable) {
            throw new OperationFailure(OperationFailureReason.UNAVAILABLE);
        }
    }
}
