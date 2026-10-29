package dev.kavrin.balance.infrastructure.sqlserver;

import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.ledger.infrastructure.persistence.*;
import java.net.http.*;

/**
 * Groups customer and funding identifiers provisioned for journal-backed opening tests.
 *
 * @param customer customer-liability fixture identity
 * @param funding funding-clearing fixture identity
 */
record AccountPair(AccountId customer, AccountId funding) { }
