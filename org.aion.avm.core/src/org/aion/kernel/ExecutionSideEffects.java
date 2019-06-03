package org.aion.kernel;

import i.RuntimeAssertionError;
import java.util.Stack;
import org.aion.types.Log;
import org.aion.vm.api.interfaces.InternalTransactionInterface;

/**
 * Records the side-effects of executing an external transaction. These side-effects are the logs
 * fired off by the external transaction or any of its internal transactions, as well as the list
 * of internal transactions spawned during execution.
 *
 * Each time an internal transaction is run, a new entry is started for that transaction, to record
 * its side-effects.
 *
 * When an internal transaction is finished, its entry is removed and its side-effects are merged
 * into its parent transaction entry.
 *
 * Finally, when all internal transactions are finished, the only entry remaining is the external
 * transaction entry, with all of the side-effects of its internal transactions now incorporated
 * into its own entry.
 */
public final class ExecutionSideEffects {
    private final Stack<SideEffects> sideEffectsStack;

    private ExecutionSideEffects() {
        this.sideEffectsStack = new Stack<>();
        this.sideEffectsStack.push(SideEffects.emptySideEffects());
    }

    public static ExecutionSideEffects newSideEffectsForExternalTransaction() {
        return new ExecutionSideEffects();
    }

    /**
     * Adds a new side effects entry for the specified internal transaction.
     */
    public void startNewInternalTransactionEntry(InternalTransactionInterface internalTransaction) {
        SideEffects sideEffects = SideEffectsUtil.addInternalTransaction(SideEffects.emptySideEffects(), internalTransaction);
        this.sideEffectsStack.push(sideEffects);
    }

    /**
     * Adds the specified log to the current side effects entry.
     */
    public void addLogToCurrentEntry(Log log) {
        SideEffects sideEffects = this.sideEffectsStack.pop();
        sideEffects = SideEffectsUtil.addLog(sideEffects, log);
        this.sideEffectsStack.push(sideEffects);
    }

    /**
     * Clears the logs and marks any internal transactions as rejected in the current side effects
     * entry.
     */
    public void clearLogsAndMarkTransactionsAsRejectedInCurrentEntry() {
        SideEffects sideEffects = this.sideEffectsStack.pop();
        sideEffects = SideEffectsUtil.clearLogsAndMarkAllTransactionsAsRejected(sideEffects);
        this.sideEffectsStack.push(sideEffects);
    }

    /**
     * Removes the current side effects entry pertaining to an internal transaction and merges it
     * with the next entry.
     */
    public void finishCurrentInternalTransactionEntry() {
        SideEffects top = this.sideEffectsStack.pop();
        SideEffects bottom = this.sideEffectsStack.pop();
        bottom = SideEffectsUtil.addLogsAndInternalTransactions(bottom, top.getExecutionLogs(), top.getInternalTransactions());
        this.sideEffectsStack.push(bottom);
    }

    /**
     * Returns true only if there are no entries at all.
     *
     * This should only ever be true only after the external transaction has been consumed.
     */
    public boolean isEmpty() {
        return this.sideEffectsStack.empty();
    }

    /**
     * Returns the side effects pertaining to the external transaction.
     *
     * This is only meaningful if the current entry is the external transaction entry (hence, we
     * are not in the middle of an internal transaction). An exception will be thrown otherwise.
     *
     * After this method returns there are no more side effects entries remaining, and nothing
     * further can be done with this data structure.
     */
    public SideEffects getExternalTransactionSideEffects() {
        SideEffects sideEffects = this.sideEffectsStack.pop();
        RuntimeAssertionError.assertTrue(this.sideEffectsStack.empty());
        return sideEffects;
    }
}
