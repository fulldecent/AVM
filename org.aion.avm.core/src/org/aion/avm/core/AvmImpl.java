package org.aion.avm.core;

import org.aion.avm.core.util.Helpers;
import org.aion.kernel.*;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import org.aion.avm.core.persistence.LoadedDApp;
import org.aion.avm.core.util.ByteArrayWrapper;
import org.aion.avm.core.util.SoftCache;
import i.IInstrumentation;
import i.IInstrumentationFactory;
import i.InstrumentationHelpers;
import i.JvmError;
import i.RuntimeAssertionError;
import org.aion.kernel.AvmTransactionResult.Code;
import org.aion.parallel.AddressResourceMonitor;
import org.aion.parallel.TransactionTask;
import org.aion.types.Address;
import org.aion.vm.api.interfaces.KernelInterface;
import org.aion.vm.api.interfaces.SimpleFuture;
import org.aion.vm.api.interfaces.TransactionInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static i.RuntimeAssertionError.unexpected;


public class AvmImpl implements AvmInternal {

    private static final Logger logger = LoggerFactory.getLogger(AvmImpl.class);
    private InternalLogger internalLogger;

    private final IInstrumentationFactory instrumentationFactory;
    private final IExternalCapabilities capabilities;

    // Long-lived state which is book-ended by the startup/shutdown calls.
    private static AvmImpl currentAvm;  // (only here for testing - makes sure that we properly clean these up between invocations)
    private SoftCache<ByteArrayWrapper, LoadedDApp> hotCache;
    private HandoffMonitor handoff;

    // Short-lived state which is reset for each batch of transaction request.
    private AddressResourceMonitor resourceMonitor;

    // Used in the case of a fatal JvmError in the background threads.  A shutdown() is the only option from this point.
    private AvmFailedException backgroundFatalError;

    private final int threadCount;
    private final boolean preserveDebuggability;
    private final boolean enableVerboseContractErrors;
    private final boolean enableVerboseConcurrentExecutor;

    public AvmImpl(IInstrumentationFactory instrumentationFactory, IExternalCapabilities capabilities, AvmConfiguration configuration) {
        this.instrumentationFactory = instrumentationFactory;
        this.capabilities = capabilities;
        // Make sure that the threadCount isn't totally invalid.
        if (configuration.threadCount < 1) {
            throw new IllegalArgumentException("Thread count must be a positive integer");
        }
        this.threadCount = configuration.threadCount;
        this.preserveDebuggability = configuration.preserveDebuggability;
        this.enableVerboseContractErrors = configuration.enableVerboseContractErrors;
        this.enableVerboseConcurrentExecutor = configuration.enableVerboseConcurrentExecutor;
        this.internalLogger = new InternalLogger(System.err);
    }

    private class AvmExecutorThread extends Thread{

        AvmExecutorThread(String name){
            super(name);
        }

        @Override
        public void run() {
            IInstrumentation instrumentation = AvmImpl.this.instrumentationFactory.createInstrumentation();
            InstrumentationHelpers.attachThread(instrumentation);
            try {
                // Run as long as we have something to do (null means shutdown).
                AvmTransactionResult outgoingResult = null;
                TransactionTask incomingTask = AvmImpl.this.handoff.blockingPollForTransaction(null, null);
                while (null != incomingTask) {
                    int abortCounter = 0;

                    do {
                        if (AvmImpl.this.enableVerboseConcurrentExecutor) {
                            System.out.println(this.getName() + " start  " + incomingTask.getIndex());
                        }

                        // Attach the IInstrumentation helper to the task to support asynchronous abort
                        // Instrumentation helper will abort the execution of the transaction by throwing an exception during chargeEnergy call
                        // Aborted transaction will be retried later
                        incomingTask.startNewTransaction();
                        incomingTask.attachInstrumentationForThread();
                        outgoingResult = AvmImpl.this.backgroundProcessTransaction(incomingTask);
                        incomingTask.detachInstrumentationForThread();

                        if (AvmTransactionResult.Code.FAILED_ABORT == outgoingResult.getResultCode()) {
                            // If this was an abort, we want to clear the abort state on the instrumentation for this thread, since
                            // this is the point where that is "handled".
                            // Note that this is safe to do here since the instrumentation isn't exposed to any other threads.
                            instrumentation.clearAbortState();
                            
                            if (AvmImpl.this.enableVerboseConcurrentExecutor) {
                                System.out.println(this.getName() + " abort  " + incomingTask.getIndex() + " counter " + (++abortCounter));
                            }
                        }
                    }while (AvmTransactionResult.Code.FAILED_ABORT == outgoingResult.getResultCode());

                    if (AvmImpl.this.enableVerboseConcurrentExecutor) {
                        System.out.println(this.getName() + " finish " + incomingTask.getIndex() + " " + outgoingResult.getResultCode());
                    }

                    incomingTask = AvmImpl.this.handoff.blockingPollForTransaction(outgoingResult, incomingTask);
                }
            } catch (JvmError e) {
                // This is a fatal error the AVM cannot generally happen so request an asynchronous shutdown.
                // We set the backgroundException without lock since any concurrently-written exception instance is equally valid.
                AvmFailedException backgroundFatalError = new AvmFailedException(e.getCause());
                AvmImpl.this.backgroundFatalError = backgroundFatalError;
                AvmImpl.this.handoff.setBackgroundThrowable(backgroundFatalError);
            } catch (Throwable t) {
                // Note that this case is primarily only relevant for unit tests or other new development which could cause internal exceptions.
                // Without this hand-off to the foreground thread, these exceptions would cause silent failures.
                // Uncaught exception - this is fatal but we need to communicate it to the outside.
                AvmImpl.this.handoff.setBackgroundThrowable(t);
            } finally {
                InstrumentationHelpers.detachThread(instrumentation);
                AvmImpl.this.instrumentationFactory.destroyInstrumentation(instrumentation);
            }
        }

    }

    public void start() {
        RuntimeAssertionError.assertTrue(null == AvmImpl.currentAvm);
        AvmImpl.currentAvm = this;
        
        RuntimeAssertionError.assertTrue(null == this.hotCache);
        this.hotCache = new SoftCache<>();

        RuntimeAssertionError.assertTrue(null == this.resourceMonitor);
        this.resourceMonitor = new AddressResourceMonitor();

        Set<Thread> executorThreads = new HashSet<>();
        for (int i = 0; i < this.threadCount; i++){
            executorThreads.add(new AvmExecutorThread("AVM Executor Thread " + i));
        }

        RuntimeAssertionError.assertTrue(null == this.handoff);
        this.handoff = new HandoffMonitor(executorThreads);
        this.handoff.startExecutorThreads();
    }

    public SimpleFuture<AvmTransactionResult>[] run(KernelInterface kernel, TransactionInterface[] transactions) throws IllegalStateException {
        if (null != this.backgroundFatalError) {
            throw this.backgroundFatalError;
        }
        // Clear the states of resources
        this.resourceMonitor.clear();

        // Clear the hot cache
        if (transactions.length > 0) {
            long currentBlockNum = kernel.getBlockNumber();
            validateCodeCache(currentBlockNum);
        }
        
        // Filter these transactions into the safe kind we use internally (the type passed in may not be well-formed).
        AvmTransaction[] safeTransactions = checkTransactions(transactions);
        // Create tasks for these new transactions and send them off to be asynchronously executed.
        TransactionTask[] tasks = new TransactionTask[transactions.length];
        for (int i = 0; i < transactions.length; i++){
            tasks[i] = new TransactionTask(kernel, safeTransactions[i], i, safeTransactions[i].senderAddress);
        }

        return this.handoff.sendTransactionsAsynchronously(tasks);
    }

    private AvmTransaction[] checkTransactions(TransactionInterface[] transactions) {
        AvmTransaction[] result = new AvmTransaction[transactions.length];
        for (int i = 0; i < result.length; ++i) {
            result[i] = AvmTransaction.from(this.capabilities, transactions[i]);
        }
        return result;
    }

    private AvmTransactionResult backgroundProcessTransaction(TransactionTask task) {
        // to capture any error during validation
        AvmTransactionResult.Code error = null;

        RuntimeAssertionError.assertTrue(task != null);
        AvmTransaction tx = task.getTransaction();
        RuntimeAssertionError.assertTrue(tx != null);

        // value/energyPrice/energyLimit sanity check
        BigInteger value = tx.value;
        if ((value.compareTo(BigInteger.ZERO) < 0) || (tx.energyPrice <= 0)) {
            error = AvmTransactionResult.Code.REJECTED;
        }
        
        if (tx.isCreate) {
            if (!task.getThisTransactionalKernel().isValidEnergyLimitForCreate(tx.energyLimit)) {
                error = AvmTransactionResult.Code.REJECTED;
            }
        } else {
            if (!task.getThisTransactionalKernel().isValidEnergyLimitForNonCreate(tx.energyLimit)) {
                error = AvmTransactionResult.Code.REJECTED;
            }
        }

        // Acquire both sender and target resources
        Address sender = tx.senderAddress;
        Address target = tx.destinationAddress;

        this.resourceMonitor.acquire(sender.toBytes(), task);
        this.resourceMonitor.acquire(target.toBytes(), task);

        // nonce check
        if (!task.getThisTransactionalKernel().accountNonceEquals(sender, tx.nonce)) {
            error = AvmTransactionResult.Code.REJECTED_INVALID_NONCE;
        }

        AvmTransactionResult result = null;
        if (null == error) {
            // The CREATE/CALL case is handled via the common external invoke path.
            result = runExternalInvoke(task.getThisTransactionalKernel(), task, tx);
        } else {
            result = new AvmTransactionResult(tx.energyLimit, tx.energyLimit);
            result.setResultCode(error);
        }

        // Task transactional kernel commits are serialized through address resource monitor
        if (!this.resourceMonitor.commitKernelForTask(task, result.getResultCode().isRejected())){
            result.setResultCode(AvmTransactionResult.Code.FAILED_ABORT);
        }

        if (AvmTransactionResult.Code.FAILED_ABORT != result.getResultCode()){
            result.setKernelInterface(task.getThisTransactionalKernel());
        }

        return result;
    }

    public void shutdown() {
        // Note that we can fail due to either a RuntimeException or an Error, so catch either and be explicit about re-throwing.
        Error errorDuringShutdown = null;
        RuntimeException exceptionDuringShutdown = null;
        try {
            this.handoff.stopAndWaitForShutdown();
        } catch (RuntimeException e) {
            // Note that this is usually the same instance as backgroundFatalError can fail for other reasons.  Catch this, complete
            // the shutdown, then re-throw it.
            exceptionDuringShutdown = e;
        } catch (Error e) {
            // Same thing for Error.
            errorDuringShutdown = e;
        }
        this.handoff = null;
        RuntimeAssertionError.assertTrue(this == AvmImpl.currentAvm);
        AvmImpl.currentAvm = null;
        this.hotCache = null;
        
        // Note that we don't want to hide the background exception, if one happened, but we do want to complete the shutdown, so we do this at the end.
        if (null != errorDuringShutdown) {
            throw errorDuringShutdown;
        }
        if (null != exceptionDuringShutdown) {
            throw exceptionDuringShutdown;
        }
        if (null != this.backgroundFatalError) {
            throw this.backgroundFatalError;
        }
    }

    @Override
    public AvmTransactionResult runInternalTransaction(KernelInterface parentKernel, TransactionTask task, AvmTransaction tx) {
        if (null != this.backgroundFatalError) {
            throw this.backgroundFatalError;
        }
        RuntimeAssertionError.assertTrue(!task.isSideEffectsStackEmpty());
        task.pushSideEffects(new SideEffects());
        AvmTransactionResult result = commonInvoke(parentKernel, task, tx, 0);
        SideEffects txSideEffects = task.popSideEffects();
        if (!result.getResultCode().isSuccess()) {
            txSideEffects.getExecutionLogs().clear();
            txSideEffects.markAllInternalTransactionsAsRejected();
        }
        task.peekSideEffects().merge(txSideEffects);
        return result;
    }

    private AvmTransactionResult runExternalInvoke(KernelInterface parentKernel, TransactionTask task, AvmTransaction tx) {
        // to capture any error during validation
        AvmTransactionResult.Code error = null;

        // Sanity checks around energy pricing and nonce are done in the caller.
        // balance check
        Address sender = tx.senderAddress;
        long energyPrice = tx.energyPrice;
        BigInteger value = tx.value;
        BigInteger transactionCost = BigInteger.valueOf(tx.energyLimit).multiply(BigInteger.valueOf(energyPrice)).add(value);
        if (!parentKernel.accountBalanceIsAtLeast(sender, transactionCost)) {
            error = AvmTransactionResult.Code.REJECTED_INSUFFICIENT_BALANCE;
        }

        // exit if validation check fails
        if (error != null) {
            AvmTransactionResult result = new AvmTransactionResult(tx.energyLimit, tx.energyLimit);
            result.setResultCode(error);
            return result;
        }

        /*
         * After this point, no rejection should occur.
         */

        // Deduct the total energy cost
        parentKernel.adjustBalance(sender, BigInteger.valueOf(tx.energyLimit).multiply(BigInteger.valueOf(energyPrice).negate()));

        // Run the common logic with the parent kernel as the top-level one.
        AvmTransactionResult result = commonInvoke(parentKernel, task, tx, BillingRules.getBasicTransactionCost(tx.data));

        // Refund energy for transaction
        BigInteger refund = BigInteger.valueOf(result.getEnergyRemaining()).multiply(BigInteger.valueOf(energyPrice));
        parentKernel.refundAccount(sender, refund);

        // Transfer fees to miner
        parentKernel.adjustBalance(parentKernel.getMinerAddress(), BigInteger.valueOf(result.getEnergyUsed()).multiply(BigInteger.valueOf(energyPrice)));

        if (!result.getResultCode().isSuccess()) {
            task.peekSideEffects().getExecutionLogs().clear();
            task.peekSideEffects().markAllInternalTransactionsAsRejected();
        }

        return result;
    }

    private AvmTransactionResult commonInvoke(KernelInterface parentKernel, TransactionTask task, AvmTransaction tx, long transactionBaseCost) {
        if (logger.isDebugEnabled()) {
            logger.debug("Transaction: address = {}, caller = {}, value = {}, data = {}, energyLimit = {}",
                tx.destinationAddress,
                tx.senderAddress,
                Helpers.bytesToHexString(tx.value.toByteArray()),
                Helpers.bytesToHexString(tx.data),
                tx.energyLimit);
        }
        // Invoke calls must build their transaction on top of an existing "parent" kernel.
        TransactionalKernel thisTransactionKernel = new TransactionalKernel(parentKernel);

        // only one result (mutable) shall be created per transaction execution
        AvmTransactionResult result = new AvmTransactionResult(tx.energyLimit, transactionBaseCost);

        // grab the recipient address as either the new contract address or the given account address.
        Address recipient = tx.destinationAddress;

        // conduct value transfer
        BigInteger value = tx.value;
        thisTransactionKernel.adjustBalance(tx.senderAddress, value.negate());
        thisTransactionKernel.adjustBalance(recipient, value);

        // At this stage, transaction can no longer be rejected.
        // The nonce increment will be done regardless of the transaction result.
        task.getThisTransactionalKernel().incrementNonce(tx.senderAddress);

        // do nothing for balance transfers of which the recipient is not a DApp address.
        if (tx.isCreate) {
            DAppCreator.create(this.capabilities, thisTransactionKernel, this, task, tx, result, this.preserveDebuggability, this.enableVerboseContractErrors);
        } else { // call
            // See if this call is trying to reenter one already on this call-stack.  If so, we will need to partially resume its state.
            ReentrantDAppStack.ReentrantState stateToResume = task.getReentrantDAppStack().tryShareState(recipient);

            LoadedDApp dapp = null;
            // The reentrant cache is obviously the first priority.
            // (note that we also want to check the kernel we were given to make sure that this DApp hasn't been deleted since we put it in the cache.
            if ((null != stateToResume) && (null != thisTransactionKernel.getTransformedCode(recipient))) {
                dapp = stateToResume.dApp;
                // Call directly and don't interact with DApp cache (we are reentering the state, not the origin of it).
                DAppExecutor.call(this.capabilities, thisTransactionKernel, this, dapp, stateToResume, task, tx, result, this.enableVerboseContractErrors);
            } else {
                // If we didn't find it there (that is only for reentrant calls so it is rarely found in the stack), try the hot DApp cache.
                ByteArrayWrapper addressWrapper = new ByteArrayWrapper(recipient.toBytes());
                LoadedDApp dappInHotCache = this.hotCache.checkout(addressWrapper);
                //'parentKernel.getTransformedCode(recipient) != null' means this recipient's DApp is not self-destructed.
                if (thisTransactionKernel.getTransformedCode(recipient) != null) {
                    dapp = dappInHotCache;
                }
                if (null == dapp) {
                    // If we didn't find it there, just load it.
                    try {
                        dapp = DAppLoader.loadFromGraph(thisTransactionKernel.getTransformedCode(recipient), this.preserveDebuggability);

                        // If the dapp is freshly loaded, we set the block num
                        if (null != dapp){
                            dapp.setLoadedBlockNum(parentKernel.getBlockNumber());
                        }

                    } catch (IOException e) {
                        unexpected(e); // the jar was created by AVM; IOException is unexpected
                    }
                }
                // Run the call and, if successful, check this into the hot DApp cache.
                if (null != dapp) {
                    DAppExecutor.call(this.capabilities, thisTransactionKernel, this, dapp, stateToResume, task, tx, result, this.enableVerboseContractErrors);
                    if (AvmTransactionResult.Code.SUCCESS == result.getResultCode()) {
                        dapp.cleanForCache();
                        this.hotCache.checkin(addressWrapper, dapp);
                    }
                }
            }
        }

        if (result.getResultCode().isSuccess()) {
            thisTransactionKernel.commit();
        } else if (result.getResultCode().equals(Code.FAILED_UNEXPECTED)) {
            internalLogger.logFatal(result.getUncaughtException());
        }

        logger.debug("Result: {}", result);
        return result;
    }

    @Override
    public AddressResourceMonitor getResourceMonitor() {
        if (null != this.backgroundFatalError) {
            throw this.backgroundFatalError;
        }
        return resourceMonitor;
    }

    private void validateCodeCache(long blockNum){
        Predicate<SoftReference<LoadedDApp>> condition = (v) -> null != v.get() && v.get().getLoadedBlockNum() >= blockNum;
        this.hotCache.removeValueIf(condition);
    }
}
