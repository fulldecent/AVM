package org.aion.kernel;

import i.RuntimeAssertionError;
import java.util.Collections;
import java.util.List;

import org.aion.types.Log;
import org.aion.vm.api.interfaces.InternalTransactionInterface;

/**
 * A class representing the side-effects that are caused by executing some external transaction.
 * These side-effects include the following data:
 *
 * 1. All of the logs generated during the execution of this transaction.
 * 2. All of the internal transactions that were spawned as a result of executing this transaction.
 */
public final class SideEffects {
    private final List<Log> logs;
    private final List<InternalTransactionInterface> internalTransactions;

    private SideEffects(List<InternalTransactionInterface> internalTransactions, List<Log> logs) {
        RuntimeAssertionError.assertTrue(internalTransactions != null);
        RuntimeAssertionError.assertTrue(logs != null);

        this.internalTransactions = Collections.unmodifiableList(internalTransactions);
        this.logs = Collections.unmodifiableList(logs);
    }

    /**
     * Constructs a new side-effects object with no internal transactions or logs.
     */
    public static SideEffects emptySideEffects() {
        return new SideEffects(Collections.emptyList(), Collections.emptyList());
    }

    /**
     * Constructs a new side-effects object with the specified internal transactions and logs.
     *
     * @param internalTransactions The internal transactions.
     * @param logs The logs.
     */
    public static SideEffects newSideEffects(List<InternalTransactionInterface> internalTransactions, List<Log> logs) {
        return new SideEffects(internalTransactions, logs);
    }

    /**
     * Returns an unmodifiable list of internal transactions.
     *
     * @return the internal transactions.
     */
    public List<InternalTransactionInterface> getInternalTransactions() {
        return this.internalTransactions;
    }

    /**
     * Returns an unmodifiable list of logs.
     *
     * @return the logs.
     */
    public List<Log> getExecutionLogs() {
        return this.logs;
    }
}
