package org.aion.kernel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.aion.aion_types.Log;
import org.aion.vm.api.interfaces.InternalTransactionInterface;

/**
 * A utility class for performing basic operations on {@link SideEffects} objects.
 */
public final class SideEffectsUtil {

    /**
     * Returns a new {@link SideEffects} object that is the same as the given sideEffects object
     * but with all of the provided internal transactions added to it.
     *
     * @param sideEffects The source side-effects.
     * @param internalTransaction The transactions to add.
     * @return the source side-effects plus the transactions.
     */
    public static SideEffects addInternalTransaction(SideEffects sideEffects, InternalTransactionInterface internalTransaction) {
        List<InternalTransactionInterface> internalTransactions = new ArrayList<>(sideEffects.getInternalTransactions());
        internalTransactions.add(internalTransaction);

        return SideEffects.newSideEffects(internalTransactions, sideEffects.getExecutionLogs());
    }

    /**
     * Returns a new {@link SideEffects} object that is the same as the given sideEffects object
     * but with the provided log added to it.
     *
     * @param sideEffects The source side-effects.
     * @param log The log to add.
     * @return the source side-effects plus the log.
     */
    public static SideEffects addLog(SideEffects sideEffects, Log log) {
        List<Log> logs = new ArrayList<>(sideEffects.getExecutionLogs());
        logs.add(log);

        return SideEffects.newSideEffects(sideEffects.getInternalTransactions(), logs);
    }

    /**
     * Returns a new {@link SideEffects} object that is the same as the given sideEffects object
     * but with the provided internal transactions and logs added to it.
     *
     * @param sideEffects The source side-effects.
     * @param logs The logs to add.
     * @param internalTransactions The internal transactions to add.
     * @return the source side-effects plus the logs and internal transactions.
     */
    public static SideEffects addLogsAndInternalTransactions(SideEffects sideEffects, List<Log> logs, List<InternalTransactionInterface> internalTransactions) {
        List<Log> allLogs = new ArrayList<>(sideEffects.getExecutionLogs());
        allLogs.addAll(logs);

        List<InternalTransactionInterface> transactions = new ArrayList<>(sideEffects.getInternalTransactions());
        transactions.addAll(internalTransactions);

        return SideEffects.newSideEffects(transactions, allLogs);
    }

    /**
     * Returns a new {@link SideEffects} object that contains no logs and all of the same internal
     * transactions as in the given sideEffects object, except with each of them marked rejected.
     *
     * @param sideEffects The source side-effects.
     * @return the internal transactions from the source side-effects but marked rejected and with no logs.
     */
    public static SideEffects clearLogsAndMarkAllTransactionsAsRejected(SideEffects sideEffects) {
        List<InternalTransactionInterface> internalTransactions = sideEffects.getInternalTransactions();
        for (InternalTransactionInterface internalTransaction : internalTransactions) {
            internalTransaction.markAsRejected();
        }
        return SideEffects.newSideEffects(internalTransactions, Collections.emptyList());
    }
}
