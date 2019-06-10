package org.aion.avm.core.testCall;

import java.math.BigInteger;

import org.aion.aion_types.AionAddress;
import org.aion.kernel.TestingTransaction;
import p.avm.Address;
import p.avm.Result;
import a.ByteArray;
import org.aion.avm.core.SimpleAvm;
import org.aion.avm.core.blockchainruntime.EmptyCapabilities;
import org.aion.avm.core.blockchainruntime.TestingBlockchainRuntime;
import org.aion.avm.core.miscvisitors.NamespaceMapper;
import org.aion.avm.core.types.InternalTransaction;
import org.aion.avm.core.util.Helpers;
import i.RuntimeAssertionError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Represents the world initState.
 */
class State {
    public State() {
    }

    public State track() {
        return new State();
    }

    public void commit() {
    }

    public void rollback() {
    }
}

/**
 * Parallel execution PoC.
 */
public class ParallelExecution {
    private static final Logger logger = LoggerFactory.getLogger(ParallelExecution.class);

    private static final int NUM_THREADS = 4;
    private static ExecutorService threadPool = Executors.newFixedThreadPool(NUM_THREADS);

    private List<TestingTransaction> transactions;
    private State initState;
    private int parallelism;
    private int startFrom;

    public ParallelExecution(List<TestingTransaction> transactions, State initState, int parallelism) {
        this.transactions = transactions;
        this.initState = initState;
        this.parallelism = parallelism;
        this.startFrom = 0;
    }

    /**
     * Executes all transaction in parallel.
     *
     * @return
     */
    public List<TransactionResult> execute() {
        State state = initState;
        List<TransactionResult> results = new ArrayList<>();

        while (startFrom < transactions.size()) {
            // set up workers
            List<Callable<TransactionResult>> callables = new ArrayList<>();
            for (int i = 0; i < parallelism && startFrom + i < transactions.size(); i++) {
                State track = state.track();
                callables.add(new Worker(transactions.get(startFrom + i), track));
            }

            try {
                // invoke all
                List<Future<TransactionResult>> futures = threadPool.invokeAll(callables);

                // detect conflicts
                Set<String> accounts = new HashSet<>();
                for (Future<TransactionResult> f : futures) {
                    TestingTransaction tx = transactions.get(startFrom);
                    TransactionResult r = f.get();

                    Set<String> set = new HashSet<>();
                    set.add(Helpers.bytesToHexString(tx.getSenderAddress().toByteArray()));
                    set.add(Helpers.bytesToHexString(tx.getDestinationAddress().toByteArray()));
                    for (InternalTransaction it : r.internalTransactions) {
                        set.add(Helpers.bytesToHexString(it.getSenderAddress().toByteArray()));
                        set.add(Helpers.bytesToHexString(it.getDestinationAddress().toByteArray()));
                    }

                    if (set.stream().anyMatch(k -> accounts.contains(k))) {
                        break;
                    }
                    r.track.commit();
                    startFrom++;
                    accounts.addAll(set);
                }
            } catch (InterruptedException e) {
                logger.info("Interrupted!");
                return null;
            } catch (ExecutionException e) {
                throw RuntimeAssertionError.unexpected(e);
            }
        }

        return results;
    }


    /**
     * Callable worker for transaction execution.
     */
    private static class Worker implements Callable<TransactionResult> {

        private TestingTransaction tx;
        private State track;
        private boolean preserveDebuggability = false;

        public Worker(TestingTransaction tx, State track) {
            this.tx = tx;
            this.track = track;
        }

        @Override
        public TransactionResult call() throws Exception {
            logger.debug("Transaction: " + tx);

            TransactionResult result = new TransactionResult();
            result.track = track;
            result.internalTransactions = new ArrayList<>();

            // Execute the transaction
            SimpleAvm avm = new SimpleAvm(tx.getEnergyLimit(), this.preserveDebuggability, Contract.class);
            avm.attachBlockchainRuntime(new TestingBlockchainRuntime(new EmptyCapabilities()) {
                @Override
                public Result avm_call(Address targetAddress, s.java.math.BigInteger value, ByteArray payload, long energyLimit) {
                    InternalTransaction internalTx = InternalTransaction.buildTransactionOfTypeCall(
                            new AionAddress(tx.getDestinationAddress().toByteArray()),
                            new AionAddress(targetAddress.toByteArray()),
                            BigInteger.ZERO,
                            value.getUnderlying(),
                            payload.getUnderlying(),
                            energyLimit,
                            tx.getEnergyPrice());
                    result.internalTransactions.add(internalTx);

                    return new Result(true, new ByteArray(new byte[0]));
                }
            }.withCaller(tx.getSenderAddress().toByteArray()).withAddress(tx.getDestinationAddress().toByteArray()).withEnergyLimit(tx.getEnergyLimit()).withData(tx.getData()));
            try {
                Class<?> clazz = avm.getClassLoader().loadUserClassByOriginalName(Contract.class.getName(), this.preserveDebuggability);
                clazz.getMethod(NamespaceMapper.mapMethodName("main")).invoke(null);
            } catch (Exception e) {
                // revert changes on failure
                result.track.rollback();
                result.internalTransactions.clear();

                logger.info("Transaction failed", e);
            }
            avm.shutdown();

            return result;
        }
    }


    /**
     * Represents the receipt of transaction.
     */
    private static class TransactionResult {
        List<InternalTransaction> internalTransactions;

        State track;
    }

    //============
    // TEST
    //============

    public static void simpleCall() {
        TestingTransaction tx1 = TestingTransaction.call(Helpers.address(1), Helpers.address(2), BigInteger.ZERO, BigInteger.ZERO, Helpers.address(3).toByteArray(), 1000000, 1);
        TestingTransaction tx2 = TestingTransaction.call(Helpers.address(3), Helpers.address(4), BigInteger.ZERO, BigInteger.ZERO, Helpers.address(1).toByteArray(), 1000000, 1);
        TestingTransaction tx3 = TestingTransaction.call(Helpers.address(3), Helpers.address(5), BigInteger.ZERO, BigInteger.ZERO, new byte[0], 1000000, 1);

        ParallelExecution exec = new ParallelExecution(List.of(tx1, tx2, tx3), new State(), NUM_THREADS);
        exec.execute();

        threadPool.shutdown();
    }

    public static void randomCall(int numAccounts, int numTransactions, int numThreads) {
        threadPool.shutdown();
        threadPool = Executors.newFixedThreadPool(numThreads);

        List<TestingTransaction> transactions = new ArrayList<>();
        Random r = new Random();
        for (int i = 0; i < numTransactions; i++) {
            int from = r.nextInt(numAccounts);
            int to = r.nextInt(numAccounts);
            int callee = r.nextInt(numAccounts);

            TestingTransaction tx = TestingTransaction.call(Helpers.address(from), Helpers.address(to), BigInteger.ZERO, BigInteger.ZERO, Helpers.address(callee).toByteArray(), 1000000, 1);
            transactions.add(tx);
        }

        long t1 = System.currentTimeMillis();
        ParallelExecution exec = new ParallelExecution(transactions, new State(), numThreads);
        exec.execute();
        long t2 = System.currentTimeMillis();
        logger.info("# of accounts = {}, # of threads = {}, # of txs = {}, Time elapsed: {}ms",
                numAccounts, numTransactions, numThreads, t2 - t1);

        threadPool.shutdown();
    }

    public static void main(String[] args) {
        // simple case, for testing
        logger.info("**** SIMPLE_TEST ****");
        simpleCall();

        // scalability test
        logger.info("**** SCALABILITY_TEST ****");
        randomCall(100, 10000, NUM_THREADS); // warm up
        logger.info("----");
        randomCall(200, 1000, 1);
        randomCall(200, 1000, 2);
        randomCall(200, 1000, 4);
        randomCall(200, 1000, 8);
        logger.info("----");
        randomCall(400, 1000, 1);
        randomCall(400, 1000, 2);
        randomCall(400, 1000, 4);
        randomCall(400, 1000, 8);
        logger.info("----");
        randomCall(800, 1000, 1);
        randomCall(800, 1000, 2);
        randomCall(800, 1000, 4);
        randomCall(800, 1000, 8);
    }
}
