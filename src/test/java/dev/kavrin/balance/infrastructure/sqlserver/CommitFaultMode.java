package dev.kavrin.balance.infrastructure.sqlserver;

/** Selects explicit test-only failure boundaries; no mode changes production transaction semantics. */
enum CommitFaultMode { NONE, AFTER_COMMIT, BEFORE_COMMIT, HIDE_COMMITTED_RESULT }
