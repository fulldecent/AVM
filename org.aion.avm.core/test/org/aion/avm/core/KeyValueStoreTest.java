package org.aion.avm.core;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.math.BigInteger;
import org.aion.types.AionAddress;
import org.aion.avm.RuntimeMethodFeeSchedule;
import org.aion.avm.StorageFees;
import org.aion.avm.core.blockchainruntime.EmptyCapabilities;
import org.aion.avm.core.dappreading.JarBuilder;
import org.aion.avm.core.util.ABIUtil;
import org.aion.avm.core.util.CodeAndArguments;
import org.aion.avm.core.util.Helpers;
import org.aion.kernel.AvmTransactionResult;
import org.aion.kernel.AvmTransactionResult.Code;
import org.aion.kernel.TestingBlock;
import org.aion.kernel.TestingKernel;
import org.aion.kernel.TestingTransaction;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class KeyValueStoreTest {
    // transaction
    private long energyLimit = 10_000_000L;
    private long energyPrice = 1L;

    // block
    private TestingBlock block = new TestingBlock(new byte[32], 1, Helpers.randomAddress(), System.currentTimeMillis(), new byte[0]);

    // kernel & vm
    private TestingKernel kernel;
    private AvmImpl avm;

    private AionAddress deployer = TestingKernel.PREMINED_ADDRESS;
    private AionAddress dappAddress;


    @Before
    public void setup() {
        this.kernel = new TestingKernel(block);

        AvmConfiguration avmConfig = new AvmConfiguration();
        this.avm = CommonAvmFactory.buildAvmInstanceForConfiguration(new EmptyCapabilities(), avmConfig);

        byte[] jar = JarBuilder.buildJarForMainAndClassesAndUserlib(KeyValueStoreTestTarget.class);
        TestingTransaction tx = TestingTransaction.create(deployer, kernel.getNonce(deployer), BigInteger.ZERO, new CodeAndArguments(jar, null).encodeToBytes(), energyLimit, energyPrice);
        AvmTransactionResult txResult = avm.run(this.kernel, new TestingTransaction[] {tx})[0].get();
        assertEquals(Code.SUCCESS, txResult.getResultCode());
        dappAddress = new AionAddress(txResult.getReturnData());
    }

    @After
    public void tearDown() {
        this.avm.shutdown();
    }

    @Test
    public void testGetStorageKeyNotExist() {
        byte[] key = Helpers.randomBytes(32);
        byte[] data = ABIUtil.encodeMethodArguments("testAvmGetStorage", key);
        TestingTransaction tx = TestingTransaction.call(deployer, dappAddress, kernel.getNonce(deployer), BigInteger.ZERO, data, energyLimit, energyPrice);
        AvmTransactionResult txResult = (AvmTransactionResult) avm.run(this.kernel, new TestingTransaction[] {tx})[0].get();

        assertEquals(Code.SUCCESS, txResult.getResultCode());
        assertArrayEquals(null, txResult.getReturnData());
    }

    @Test
    public void testGetStorageWrongSizeKey() {
        byte[] key = Helpers.randomBytes(33);
        byte[] data = ABIUtil.encodeMethodArguments("testAvmGetStorage", key);
        TestingTransaction tx = TestingTransaction.call(deployer, dappAddress, kernel.getNonce(deployer), BigInteger.ZERO, data, energyLimit, energyPrice);
        AvmTransactionResult txResult = (AvmTransactionResult) avm.run(this.kernel, new TestingTransaction[] {tx})[0].get();

        assertEquals(Code.FAILED_EXCEPTION, txResult.getResultCode());
    }

    @Test
    public void testPutStorageWrongSizeKey() {
        byte[] key = Helpers.randomBytes(33);
        byte[] value = Helpers.randomBytes(32);

        byte[] data = ABIUtil.encodeMethodArguments("testAvmPutStorage", key, value);
        TestingTransaction tx = TestingTransaction.call(deployer, dappAddress, kernel.getNonce(deployer), BigInteger.ZERO, data, energyLimit, energyPrice);
        AvmTransactionResult txResult = (AvmTransactionResult) avm.run(this.kernel, new TestingTransaction[] {tx})[0].get();

        assertEquals(Code.FAILED_EXCEPTION, txResult.getResultCode());
    }

    @Test
    public void testPutGetStorageSuccess() {
        byte[] key = Helpers.randomBytes(32);
        byte[] value = Helpers.randomBytes(32);

        byte[] data = ABIUtil.encodeMethodArguments("testAvmPutStorage", key, value);
        TestingTransaction tx = TestingTransaction.call(deployer, dappAddress, kernel.getNonce(deployer), BigInteger.ZERO, data, energyLimit, energyPrice);
        AvmTransactionResult txResult = (AvmTransactionResult) avm.run(this.kernel, new TestingTransaction[] {tx})[0].get();

        assertEquals(Code.SUCCESS, txResult.getResultCode());
        assertArrayEquals(new byte[0], txResult.getReturnData());

        data = ABIUtil.encodeMethodArguments("testAvmGetStorage", key);
        tx = TestingTransaction.call(deployer, dappAddress, kernel.getNonce(deployer), BigInteger.ZERO, data, energyLimit, energyPrice);
        txResult = (AvmTransactionResult) avm.run(this.kernel, new TestingTransaction[] {tx})[0].get();

        assertEquals(Code.SUCCESS, txResult.getResultCode());
        assertArrayEquals(value, txResult.getReturnData());
    }

    @Test
    public void testStoragePutNullDelete() {
        byte[] key = Helpers.randomBytes(32);
        byte[] value = Helpers.randomBytes(32);

        byte[] data = ABIUtil.encodeMethodArguments("testAvmPutStorage", key, value);
        TestingTransaction tx = TestingTransaction.call(deployer, dappAddress, kernel.getNonce(deployer), BigInteger.ZERO, data, energyLimit, energyPrice);
        AvmTransactionResult txResult = (AvmTransactionResult) avm.run(this.kernel, new TestingTransaction[] {tx})[0].get();

        assertEquals(Code.SUCCESS, txResult.getResultCode());
        assertArrayEquals(new byte[0], txResult.getReturnData());

        data = ABIUtil.encodeMethodArguments("testAvmGetStorage", key);
        tx = TestingTransaction.call(deployer, dappAddress, kernel.getNonce(deployer), BigInteger.ZERO, data, energyLimit, energyPrice);
        txResult = (AvmTransactionResult) avm.run(this.kernel, new TestingTransaction[] {tx})[0].get();

        assertEquals(Code.SUCCESS, txResult.getResultCode());
        assertArrayEquals(value, txResult.getReturnData());

        // put null to delete
        data = ABIUtil.encodeMethodArguments("testAvmPutStorageNullValue", key);
        tx = TestingTransaction.call(deployer, dappAddress, kernel.getNonce(deployer), BigInteger.ZERO, data, energyLimit, energyPrice);
        txResult = (AvmTransactionResult) avm.run(this.kernel, new TestingTransaction[] {tx})[0].get();

        assertEquals(Code.SUCCESS, txResult.getResultCode());
        assertArrayEquals(new byte[0], txResult.getReturnData());

        data = ABIUtil.encodeMethodArguments("testAvmGetStorage", key);
        tx = TestingTransaction.call(deployer, dappAddress, kernel.getNonce(deployer), BigInteger.ZERO, data, energyLimit, energyPrice);
        txResult = (AvmTransactionResult) avm.run(this.kernel, new TestingTransaction[] {tx})[0].get();

        assertEquals(Code.SUCCESS, txResult.getResultCode());
        assertArrayEquals(null, txResult.getReturnData());
    }

    @Test
    public void testStorageBilling() {
        byte[] key = new byte[]{93, -35, 110, 84, -89, -100, 19, 127, -13, 28, -39, 74, -110, -26, 13, -22, -30, 108, 115, 17, 57, 54, 74, -90, 45, -35, -21, 39, 109, -3, 111, -105};
        byte[] value= new byte[]{124, 22, 37, 17, -40, 97, -46, -103, -38, 48, 46, -115, -78, -5, -116, -15, 81, 94, -61, 52, -68, 73, -35, -34, -44, -82, -43, 68, -32, 100, -124, -124};

        // zero -> zero
        byte[] data = ABIUtil.encodeMethodArguments("testAvmPutStorageNullValue", key);
        TestingTransaction tx = TestingTransaction.call(deployer, dappAddress, kernel.getNonce(deployer), BigInteger.ZERO, data, energyLimit, energyPrice);
        AvmTransactionResult txResult = (AvmTransactionResult) avm.run(this.kernel, new TestingTransaction[] {tx})[0].get();
        assertEquals(Code.SUCCESS, txResult.getResultCode());
        assertArrayEquals(new byte[0], txResult.getReturnData());
        long deleteZeroCost = txResult.getEnergyUsed();
        assertEquals(51680L + RuntimeMethodFeeSchedule.BlockchainRuntime_avm_resetStorage, deleteZeroCost);

        // zero -> nonzero
        data = ABIUtil.encodeMethodArguments("testAvmPutStorage", key, value);
        tx = TestingTransaction.call(deployer, dappAddress, kernel.getNonce(deployer), BigInteger.ZERO, data, energyLimit, energyPrice);
        txResult = (AvmTransactionResult) avm.run(this.kernel, new TestingTransaction[] {tx})[0].get();
        assertEquals(Code.SUCCESS, txResult.getResultCode());
        assertArrayEquals(new byte[0], txResult.getReturnData());
        long setStorageCost = txResult.getEnergyUsed();
        assertEquals(55007L + RuntimeMethodFeeSchedule.BlockchainRuntime_avm_setStorage + StorageFees.WRITE_PRICE_PER_BYTE * value.length, setStorageCost);

        // nonzero -> nonzero
        data = ABIUtil.encodeMethodArguments("testAvmPutStorage", key, value);
        tx = TestingTransaction.call(deployer, dappAddress, kernel.getNonce(deployer), BigInteger.ZERO, data, energyLimit, energyPrice);
        txResult = (AvmTransactionResult) avm.run(this.kernel, new TestingTransaction[] {tx})[0].get();
        assertEquals(Code.SUCCESS, txResult.getResultCode());
        assertArrayEquals(new byte[0], txResult.getReturnData());
        long modifyStorageCost = txResult.getEnergyUsed();
        assertEquals(55007L + RuntimeMethodFeeSchedule.BlockchainRuntime_avm_resetStorage + StorageFees.WRITE_PRICE_PER_BYTE * value.length, modifyStorageCost);
        // set storage cost 20000 + linear factor cost, modify storage cost 5000
        assertEquals(15000L, setStorageCost - modifyStorageCost);

        // get nonzero
        data = ABIUtil.encodeMethodArguments("testAvmGetStorage", key);
        tx = TestingTransaction.call(deployer, dappAddress, kernel.getNonce(deployer), BigInteger.ZERO, data, energyLimit, energyPrice);
        txResult = (AvmTransactionResult) avm.run(this.kernel, new TestingTransaction[] {tx})[0].get();
        assertEquals(Code.SUCCESS, txResult.getResultCode());
        long getStorageCost = txResult.getEnergyUsed();
        assertEquals(49283L + RuntimeMethodFeeSchedule.BlockchainRuntime_avm_getStorage + StorageFees.READ_PRICE_PER_BYTE * value.length, getStorageCost);

        // nonzero -> zero
        data = ABIUtil.encodeMethodArguments("testAvmPutStorageNullValue", key);
        tx = TestingTransaction.call(deployer, dappAddress, kernel.getNonce(deployer), BigInteger.ZERO, data, energyLimit, energyPrice);
        txResult = (AvmTransactionResult) avm.run(this.kernel, new TestingTransaction[] {tx})[0].get();
        assertEquals(Code.SUCCESS, txResult.getResultCode());
        assertArrayEquals(new byte[0], txResult.getReturnData());
        long deleteStorageCost = txResult.getEnergyUsed();
        assertEquals(51680 + RuntimeMethodFeeSchedule.BlockchainRuntime_avm_resetStorage - RuntimeMethodFeeSchedule.BlockchainRuntime_avm_deleteStorage_refund, deleteStorageCost);
        // both deletion cost 5000, but deleting a non-zero value gets 20000 refund
        assertEquals(15000L, deleteZeroCost - deleteStorageCost);

        // get zero
        data = ABIUtil.encodeMethodArguments("testAvmGetStorage", key);
        tx = TestingTransaction.call(deployer, dappAddress, kernel.getNonce(deployer), BigInteger.ZERO, data, energyLimit, energyPrice);
        txResult = (AvmTransactionResult) avm.run(this.kernel, new TestingTransaction[] {tx})[0].get();
        assertEquals(Code.SUCCESS, txResult.getResultCode());
        assertArrayEquals(null, txResult.getReturnData());
        long getZeroCost = txResult.getEnergyUsed();
        assertEquals(49283 + RuntimeMethodFeeSchedule.BlockchainRuntime_avm_getStorage, getZeroCost);
        assertEquals(value.length * StorageFees.READ_PRICE_PER_BYTE, getStorageCost - getZeroCost);
    }
}
