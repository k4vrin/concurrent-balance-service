package dev.kavrin.balance.infrastructure.sqlserver;

import dev.kavrin.balance.account.domain.*;
import dev.kavrin.balance.ledger.domain.*;

/**
 * Selects test-only write boundaries at which an opening attempt fails.
 *
 * <p>Injected failures demonstrate rollback of earlier writes; NONE permits the complete opening path.
 */
enum OpeningFailurePoint { NONE, AFTER_JOURNAL, AFTER_POSTINGS, AFTER_PROJECTIONS }
