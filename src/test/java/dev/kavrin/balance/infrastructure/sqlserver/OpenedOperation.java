package dev.kavrin.balance.infrastructure.sqlserver;

import dev.kavrin.balance.account.domain.*;
import dev.kavrin.balance.ledger.domain.*;
import java.util.UUID;

record OpenedOperation(UUID operationId, UUID journalId, String transactionId, byte[] canonical) { }
