package dev.kavrin.balance.infrastructure.sqlserver;

import dev.kavrin.balance.account.domain.*;
import dev.kavrin.balance.ledger.domain.*;

enum OpeningFailurePoint { NONE, AFTER_JOURNAL, AFTER_POSTINGS, AFTER_PROJECTIONS }
