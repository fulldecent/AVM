package org.aion.avm.core;

import java.math.BigInteger;
import org.aion.avm.core.blockchainruntime.EmptyCapabilities;
import org.aion.avm.core.dappreading.JarBuilder;
import org.aion.avm.core.util.CodeAndArguments;
import org.aion.avm.core.util.ABIUtil;
import org.aion.avm.core.util.Helpers;
import org.aion.kernel.AvmTransactionResult;
import org.aion.kernel.TestingBlock;
import org.aion.kernel.TestingKernel;
import org.aion.kernel.TestingTransaction;
import org.aion.types.Address;
import org.aion.vm.api.interfaces.InternalTransactionInterface;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;


public class AvmFailureTest {
    // transaction
    private long energyLimit = 10_000_000L;
    private long energyPrice = 1L;

    // block
    private TestingBlock block = new TestingBlock(new byte[32], 1, Helpers.randomAddress(), System.currentTimeMillis(), new byte[0]);

    // kernel & vm
    private TestingKernel kernel;
    private AvmImpl avm;

    private Address deployer = TestingKernel.PREMINED_ADDRESS;
    private Address dappAddress;

    @Before
    public void setup() {
        this.kernel = new TestingKernel(block);
        this.avm = CommonAvmFactory.buildAvmInstanceForConfiguration(new EmptyCapabilities(), new AvmConfiguration());
        
        byte[] jar = JarBuilder.buildJarForMainAndClassesAndUserlib(AvmFailureTestResource.class);
        byte[] arguments = null;
        TestingTransaction tx = TestingTransaction.create(deployer, kernel.getNonce(deployer), BigInteger.ZERO, new CodeAndArguments(jar, arguments).encodeToBytes(), energyLimit, energyPrice);
        AvmTransactionResult txResult = avm.run(this.kernel, new TestingTransaction[] {tx})[0].get();

        dappAddress = Address.wrap(txResult.getReturnData());
        assertTrue(null != dappAddress);
    }

    @After
    public void tearDown() {
        this.avm.shutdown();
    }

    @Test
    public void testFailedTransaction() {
        byte[] data = ABIUtil.encodeMethodArguments("reentrantCall", 5);
        TestingTransaction tx = TestingTransaction.call(deployer, dappAddress, kernel.getNonce(deployer), BigInteger.ZERO, data, energyLimit, energyPrice);
        AvmTransactionResult txResult = (AvmTransactionResult) avm.run(this.kernel, new TestingTransaction[] {tx})[0].get();

        assertEquals(AvmTransactionResult.Code.FAILED_REVERT, txResult.getResultCode());
        assertEquals(5, txResult.getSideEffects().getInternalTransactions().size());
        assertEquals(0, txResult.getSideEffects().getExecutionLogs().size());

        for (InternalTransactionInterface i : txResult.getSideEffects().getInternalTransactions()) {
            assertTrue(i.isRejected());
        }
    }

    @Test
    public void testOutOfEnergy() {
        byte[] data = ABIUtil.encodeMethodArguments("testOutOfEnergy");
        TestingTransaction tx = TestingTransaction.call(deployer, dappAddress, kernel.getNonce(deployer), BigInteger.ZERO, data, energyLimit, energyPrice);
        AvmTransactionResult txResult = avm.run(this.kernel, new TestingTransaction[] {tx})[0].get();

        assertEquals(AvmTransactionResult.Code.FAILED_OUT_OF_ENERGY, txResult.getResultCode());
    }

    @Test
    public void testOutOfStack() {
        byte[] data = ABIUtil.encodeMethodArguments("testOutOfStack");
        TestingTransaction tx = TestingTransaction.call(deployer, dappAddress, kernel.getNonce(deployer), BigInteger.ZERO, data, energyLimit, energyPrice);
        AvmTransactionResult txResult = avm.run(this.kernel, new TestingTransaction[] {tx})[0].get();

        assertEquals(AvmTransactionResult.Code.FAILED_OUT_OF_STACK, txResult.getResultCode());
    }

    @Test
    public void testRevert() {
        byte[] data = ABIUtil.encodeMethodArguments("testRevert");
        TestingTransaction tx = TestingTransaction.call(deployer, dappAddress, kernel.getNonce(deployer), BigInteger.ZERO, data, energyLimit, energyPrice);
        AvmTransactionResult txResult = (AvmTransactionResult) avm.run(this.kernel, new TestingTransaction[] {tx})[0].get();

        assertEquals(AvmTransactionResult.Code.FAILED_REVERT, txResult.getResultCode());
        assertNotEquals(energyLimit, txResult.getEnergyUsed());
        assertNotEquals(0, txResult.getEnergyRemaining());
    }

    @Test
    public void testInvalid() {
        byte[] data = ABIUtil.encodeMethodArguments("testInvalid");
        TestingTransaction tx = TestingTransaction.call(deployer, dappAddress, kernel.getNonce(deployer), BigInteger.ZERO, data, energyLimit, energyPrice);
        AvmTransactionResult txResult = (AvmTransactionResult) avm.run(this.kernel, new TestingTransaction[] {tx})[0].get();

        assertEquals(AvmTransactionResult.Code.FAILED_INVALID, txResult.getResultCode());
        assertEquals(energyLimit, txResult.getEnergyUsed());
        assertEquals(0, txResult.getEnergyRemaining());
    }

    @Test
    public void testUncaughtException() {
        byte[] data = ABIUtil.encodeMethodArguments("testUncaughtException");
        TestingTransaction tx = TestingTransaction.call(deployer, dappAddress, kernel.getNonce(deployer), BigInteger.ZERO, data, energyLimit, energyPrice);
        AvmTransactionResult txResult = (AvmTransactionResult) avm.run(this.kernel, new TestingTransaction[] {tx})[0].get();

        assertEquals(AvmTransactionResult.Code.FAILED_EXCEPTION, txResult.getResultCode());
        assertTrue(txResult.getUncaughtException() instanceof RuntimeException);
    }

    @Test
    public void testInvalidTransaction() {
        // We will encode a transaction with invalid data (null data).
        byte[] data = null;
        TestingTransaction falingTransaction = TestingTransaction.call(deployer, dappAddress, kernel.getNonce(deployer), BigInteger.ZERO, data, energyLimit, energyPrice);
        boolean didFail = false;
        // This next call should fail with IllegalArgumentException so catch it.
        try {
            avm.run(this.kernel, new TestingTransaction[] {falingTransaction})[0].get();
        } catch (IllegalArgumentException e) {
            didFail = true;
        }
        Assert.assertTrue(didFail);
        
        // Make sure that we haven't corrupted the system and can still deploy new things
        byte[] jar = JarBuilder.buildJarForMainAndClassesAndUserlib(AvmFailureTestResource.class);
        byte[] arguments = null;
        TestingTransaction tx = TestingTransaction.create(deployer, kernel.getNonce(deployer), BigInteger.ZERO, new CodeAndArguments(jar, arguments).encodeToBytes(), energyLimit, energyPrice);
        AvmTransactionResult txResult = avm.run(this.kernel, new TestingTransaction[] {tx})[0].get();
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, txResult.getResultCode());
        Assert.assertNotNull(Address.wrap(txResult.getReturnData()));
    }
}
