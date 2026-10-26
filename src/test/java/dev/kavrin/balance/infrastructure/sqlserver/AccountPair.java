package dev.kavrin.balance.infrastructure.sqlserver;

import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.ledger.infrastructure.persistence.*;
import java.net.http.*;

record AccountPair(AccountId customer, AccountId funding) { }
