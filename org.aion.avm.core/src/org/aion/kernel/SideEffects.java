package org.aion.kernel;

import i.RuntimeAssertionError;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.aion.types.AionAddress;
import org.aion.vm.api.interfaces.IExecutionLog;
import org.aion.vm.api.interfaces.InternalTransactionInterface;

/**
 * A class representing the side-effects that are caused by executing some external transaction.
 * These side-effects include the following data:
 *
 * 1. All of the logs generated during the execution of this transaction.
 * 2. All of the addressed that were marked to be deleted during the execution of this transaction.
 * 3. All of the internal transactions that were spawned as a result of executing this transaction.
 */
public class SideEffects {
    private List<IExecutionLog> logs;
    private List<InternalTransactionInterface> internalTransactions;

    /**
     * Constructs a new empty {@code SideEffects}.
     */
    public SideEffects() {
        this.logs = new ArrayList<>();
        this.internalTransactions = new ArrayList<>();
    }

    public SideEffects(List<InternalTransactionInterface> internalTransactions, List<IExecutionLog> logs) {
        this.logs = new ArrayList<>();
        this.internalTransactions = new ArrayList<>();

        this.logs.addAll(logs);
        this.internalTransactions.addAll(internalTransactions);
    }

    public void merge(SideEffects sideEffects) {
        throw RuntimeAssertionError.unimplemented("unimplemented.");
    }

    public void markAllInternalTransactionsAsRejected() {
        throw RuntimeAssertionError.unimplemented("unimplemented.");
    }

    public void addInternalTransaction(InternalTransactionInterface transaction) {
        throw RuntimeAssertionError.unimplemented("unimplemented.");
    }

    public void addInternalTransactions(List<InternalTransactionInterface> transactions) {
        throw RuntimeAssertionError.unimplemented("unimplemented.");
    }

    public void addToDeletedAddresses(AionAddress address) {
        throw RuntimeAssertionError.unimplemented("unimplemented.");
    }

    public void addAllToDeletedAddresses(Collection<AionAddress> addresses) {
        throw RuntimeAssertionError.unimplemented("unimplemented.");
    }

    public void addLog(IExecutionLog log) {
        throw RuntimeAssertionError.unimplemented("unimplemented.");
    }

    public void addLogs(Collection<IExecutionLog> logs) {
        throw RuntimeAssertionError.unimplemented("unimplemented.");
    }

    public List<InternalTransactionInterface> getInternalTransactions() {
        return this.internalTransactions;
    }

    public List<AionAddress> getAddressesToBeDeleted() {
        return new ArrayList<>();
    }

    public List<IExecutionLog> getExecutionLogs() {
        return this.logs;
    }

}
