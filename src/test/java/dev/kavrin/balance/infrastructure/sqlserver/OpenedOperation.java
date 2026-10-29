package dev.kavrin.balance.infrastructure.sqlserver;

import dev.kavrin.balance.account.domain.*;
import dev.kavrin.balance.ledger.domain.*;
import java.util.UUID;

/**
 * Retains identities and canonical evidence returned by a committed opening fixture.
 *
 * @param operationId opening operation UUID
 * @param journalId associated journal UUID
 * @param transactionId controlled opening retry identity
 * @param canonical canonical request bytes used by fixture assertions
 */
record OpenedOperation(UUID operationId, UUID journalId, String transactionId, byte[] canonical) { }
