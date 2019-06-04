package org.aion.avm.tooling;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.aion.aion_types.Log;
import org.aion.avm.core.util.ABIUtil;
import org.aion.avm.core.AvmConfiguration;
import org.aion.avm.core.AvmImpl;
import org.aion.avm.core.CommonAvmFactory;
import org.aion.avm.core.dappreading.JarBuilder;
import org.aion.avm.core.util.CodeAndArguments;
import org.aion.avm.core.util.Helpers;
import org.aion.avm.core.util.LogSizeUtils;
import org.aion.kernel.AvmTransactionResult;
import org.aion.kernel.TestingBlock;
import org.aion.kernel.TestingKernel;
import org.aion.kernel.TestingTransaction;
import org.aion.types.Address;
import org.aion.vm.api.interfaces.KernelInterface;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class ContractLoggingTest {
    private static final int NUM_LOGS = 5;

    private static Address from = TestingKernel.PREMINED_ADDRESS;
    private static long energyLimit = 5_000_000L;
    private static long energyPrice = 1;
    private static TestingBlock block = new TestingBlock(new byte[32], 1, Helpers.randomAddress(), System.currentTimeMillis(), new byte[0]);

    private static KernelInterface kernel;
    private static AvmImpl avm;
    private static Address contract;

    private List<Integer> counts = new ArrayList<>();

    @BeforeClass
    public static void setup() {
        kernel = new TestingKernel(block);
        avm = CommonAvmFactory.buildAvmInstanceForConfiguration(new StandardCapabilities(), new AvmConfiguration());
        deployContract();
    }

    @AfterClass
    public static void tearDown() {
        avm.shutdown();
    }

    @Test
    public void testLogs() {
        TestingTransaction transaction = generateTxForMethodCall("hitLogs");
        AvmTransactionResult result = runTransaction(transaction);
        assertTrue(result.getResultCode().isSuccess());

        assertEquals(NUM_LOGS, result.getLogs().size());
        assertEquals(0, result.getInternalTransactions().size());

        verifyLogs(result.getLogs(), 1);
    }

    @Test
    public void testLogsFireOffInDeepestInternalTransaction() {
        TestingTransaction transaction = generateTxForMethodCall("spawnInternalTransactionsAndHitLogsAtBottomLevel", 9);
        AvmTransactionResult result = runTransaction(transaction);
        assertTrue(result.getResultCode().isSuccess());

        assertEquals(NUM_LOGS, result.getLogs().size());
        assertEquals(9, result.getInternalTransactions().size());

        verifyLogs(result.getLogs(), 1);
    }

    @Test
    public void testLogsFiredOffInEachInternalTransaction() {
        int depth = 9;

        TestingTransaction transaction = generateTxForMethodCall("spawnInternalTransactionsAndHitLogsAtEachLevel", depth);
        AvmTransactionResult result = runTransaction(transaction);
        assertTrue(result.getResultCode().isSuccess());

        assertEquals(NUM_LOGS * (depth + 1), result.getLogs().size());
        assertEquals(depth, result.getInternalTransactions().size());

        verifyLogs(result.getLogs(), depth + 1);
    }

    @Test
    public void testLogsFiredOffInEachInternalTransactionUptoFive() {
        TestingTransaction transaction = generateTxForMethodCall("spawnInternalTransactionsAndFailAtDepth5", 9);
        AvmTransactionResult result = runTransaction(transaction);
        assertTrue(result.getResultCode().isSuccess());

        assertEquals(NUM_LOGS * 5, result.getLogs().size());
        assertEquals(5, result.getInternalTransactions().size());

        verifyLogs(result.getLogs(), 5);
    }

    /**
     * Checks that each of the logs is in its expected state and that it has been generated the
     * appropriate number of times.
     *
     * If anything fails to check out here the calling test will fail.
     */
    private void verifyLogs(List<Log> logs, int numCallsToHitLogs) {
        resetCounters();
        for (Log log : logs) {
            verifyLog(log);
        }
        verifyCounts(numCallsToHitLogs);
    }

    /**
     * Checks that each of the counters is equal to numCalls.
     */
    private void verifyCounts(int numCalls) {
        for (Integer count : this.counts) {
            assertEquals(numCalls, count.intValue());
        }
    }

    /**
     * Verifies that log is one of the 5 possible logging calls in the contract.
     */
    private void verifyLog(Log log) {
        assertEquals(contract, new Address(log.copyOfAddress()));
        switch (log.copyOfTopics().size()) {
            case 0:
                assertArrayEquals(LoggingTarget.DATA1, log.copyOfData());
                incrementCounter(0);
                break;
            case 1:
                assertArrayEquals(LogSizeUtils.truncatePadTopic(LoggingTarget.TOPIC1), log.copyOfTopics().get(0));
                assertArrayEquals(LoggingTarget.DATA2, log.copyOfData());
                incrementCounter(1);
                break;
            case 2:
                assertArrayEquals(LogSizeUtils.truncatePadTopic(LoggingTarget.TOPIC1), log.copyOfTopics().get(0));
                assertArrayEquals(LogSizeUtils.truncatePadTopic(LoggingTarget.TOPIC2), log.copyOfTopics().get(1));
                assertArrayEquals(LoggingTarget.DATA3, log.copyOfData());
                incrementCounter(2);
                break;
            case 3:
                assertArrayEquals(LogSizeUtils.truncatePadTopic(LoggingTarget.TOPIC1), log.copyOfTopics().get(0));
                assertArrayEquals(LogSizeUtils.truncatePadTopic(LoggingTarget.TOPIC2), log.copyOfTopics().get(1));
                assertArrayEquals(LogSizeUtils.truncatePadTopic(LoggingTarget.TOPIC3), log.copyOfTopics().get(2));
                assertArrayEquals(LoggingTarget.DATA4, log.copyOfData());
                incrementCounter(3);
                break;
            case 4:
                assertArrayEquals(LogSizeUtils.truncatePadTopic(LoggingTarget.TOPIC1), log.copyOfTopics().get(0));
                assertArrayEquals(LogSizeUtils.truncatePadTopic(LoggingTarget.TOPIC2), log.copyOfTopics().get(1));
                assertArrayEquals(LogSizeUtils.truncatePadTopic(LoggingTarget.TOPIC3), log.copyOfTopics().get(2));
                assertArrayEquals(LogSizeUtils.truncatePadTopic(LoggingTarget.TOPIC4), log.copyOfTopics().get(3));
                assertArrayEquals(LoggingTarget.DATA5, log.copyOfData());
                incrementCounter(4);
                break;
            default:
                fail("Log topic size should be in the range [0,4] but was: " + log.copyOfTopics().size());
        }
    }

    private static void deployContract() {
        byte[] jar = JarBuilder.buildJarForMainAndClassesAndUserlib(LoggingTarget.class);
        jar = new CodeAndArguments(jar, new byte[0]).encodeToBytes();

        TestingTransaction transaction = TestingTransaction.create(from, kernel.getNonce(from), BigInteger.ZERO, jar, energyLimit, energyPrice);
        AvmTransactionResult result = avm.run(ContractLoggingTest.kernel, new TestingTransaction[] {transaction})[0].get();

        assertTrue(result.getResultCode().isSuccess());
        contract = Address.wrap(result.getReturnData());
    }

    private AvmTransactionResult runTransaction(TestingTransaction tx) {
        return avm.run(ContractLoggingTest.kernel, new TestingTransaction[] {tx})[0].get();
    }

    private TestingTransaction generateTxForMethodCall(String methodName, Object... args) {
        byte[] callData = ABIUtil.encodeMethodArguments(methodName, args);
        return TestingTransaction.call(from, contract, kernel.getNonce(from), BigInteger.ZERO, callData, energyLimit, energyPrice);
    }

    private void resetCounters() {
        this.counts.clear();
        for (int i = 0; i < 5; i++) {
            this.counts.add(0);
        }
    }

    private void incrementCounter(int position) {
        this.counts.add(position, this.counts.remove(position) + 1);
    }

}
