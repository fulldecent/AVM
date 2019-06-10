package org.aion.avm.core;

import java.math.BigInteger;

import org.aion.aion_types.AionAddress;
import org.aion.avm.core.blockchainruntime.EmptyCapabilities;
import org.aion.avm.core.dappreading.JarBuilder;
import org.aion.avm.core.testWallet.ByteArrayHelpers;
import org.aion.avm.core.testWallet.ByteArrayWrapper;
import org.aion.avm.core.testWallet.BytesKey;
import org.aion.avm.core.testWallet.CallEncoder;
import org.aion.avm.core.testWallet.Daylimit;
import org.aion.avm.core.testWallet.EventLogger;
import org.aion.avm.core.testWallet.Multiowned;
import org.aion.avm.core.testWallet.Operation;
import org.aion.avm.core.testWallet.RequireFailedException;
import org.aion.avm.core.testWallet.Wallet;
import org.aion.avm.core.util.ABIUtil;
import org.aion.avm.core.util.CodeAndArguments;
import org.aion.avm.core.util.Helpers;
import org.aion.kernel.*;
import org.aion.vm.api.interfaces.KernelInterface;
import org.aion.vm.api.interfaces.TransactionResult;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;


/**
 * Our current thinking is that we will use a JUnit launcher for the proof-of-concept demonstration.  This is that entry-point.
 * See issue-124 for more of the background.
 */
public class PocWalletTest {

    // For now, we will just reuse the from, to, and block for each call (in the future, this will change).
    private AionAddress from = TestingKernel.PREMINED_ADDRESS;
    private TestingBlock block = new TestingBlock(new byte[32], 1, Helpers.randomAddress(), System.currentTimeMillis(), new byte[0]);
    private long energyLimit = 10_000_000_000L;
    private long energyPrice = 1;

    private KernelInterface kernel;
    private AvmImpl avm;

    @Before
    public void setup() {
        this.kernel = new TestingKernel(block);
        this.avm = CommonAvmFactory.buildAvmInstanceForConfiguration(new EmptyCapabilities() {
            @Override
            public byte[] blake2b(byte[] data) {
                // NOTE:  This test relies on calling blake2b but doesn't rely on the answer being correct so just return the input.
                return data;
            }
        }, new AvmConfiguration());
    }

    @After
    public void tearDown() {
        this.avm.shutdown();
    }

    private byte[] buildTestWalletJar() {
        return JarBuilder.buildJarForMainAndClassesAndUserlib(Wallet.class
                , Multiowned.class
                , ByteArrayWrapper.class
                , Operation.class
                , ByteArrayHelpers.class
                , BytesKey.class
                , RequireFailedException.class
                , Daylimit.class
                , EventLogger.class
        );
    }

    /**
     * Tests that a deploy call will store the code for the Wallet JAR.
     * This means that it transformed it correctly and nothing was missing.
     */
    @Test
    public void testDeployWritesCode() {
        byte[] testWalletJar = buildTestWalletJar();
        byte[] testWalletArguments = new byte[0];

        TestingTransaction createTransaction = TestingTransaction.create(from, kernel.getNonce(from), BigInteger.ZERO, new CodeAndArguments(testWalletJar, testWalletArguments).encodeToBytes(), energyLimit, energyPrice);
        TransactionResult createResult = avm.run(this.kernel, new TestingTransaction[] {createTransaction})[0].get();

        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, createResult.getResultCode());
        Assert.assertNotNull(kernel.getTransformedCode(new AionAddress(createResult.getReturnData())));
    }

    /**
     * Tests that we can run init on the deployed code, albeit as a second transaction (since we haven't yet decided how to invoke init on deploy).
     */
    @Test
    public void testDeployAndCallInit() throws Exception {
        // Constructor args.
        AionAddress extra1 = Helpers.randomAddress();
        AionAddress extra2 = Helpers.randomAddress();
        int requiredVotes = 2;
        long dailyLimit = 5000;

        byte[] testWalletJar = buildTestWalletJar();
        byte[] testWalletArguments = new byte[0];
        TestingTransaction createTransaction = TestingTransaction.create(from, kernel.getNonce(
            from), BigInteger.ZERO, new CodeAndArguments(testWalletJar, testWalletArguments).encodeToBytes(), energyLimit, energyPrice);
        TransactionResult createResult = avm.run(this.kernel, new TestingTransaction[] {createTransaction})[0].get();
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, createResult.getResultCode());

        // contract address is stored in return data
        AionAddress contractAddress = new AionAddress(createResult.getReturnData());

        byte[] initArgs = CallEncoder.init(new avm.Address(extra1.toByteArray()), new avm.Address(extra2.toByteArray()), requiredVotes, dailyLimit);
        TestingTransaction initTransaction = TestingTransaction.call(from, contractAddress, kernel.getNonce(from), BigInteger.ZERO, initArgs, energyLimit, energyPrice);
        TransactionResult initResult = avm.run(this.kernel, new TestingTransaction[] {initTransaction})[0].get();
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, initResult.getResultCode());
    }

    /**
     * Tests that inner classes work properly within the serialization system (since their constructors need to be marked accessible).
     */
    @Test
    public void testExecuteWithInnerClasses() throws Exception {
        // Constructor args.
        AionAddress extra1 = Helpers.randomAddress();
        AionAddress extra2 = Helpers.randomAddress();
        int requiredVotes = 2;
        long dailyLimit = 5000;

        // Deploy.
        AionAddress contractAddress = new AionAddress(deployTestWallet());

        // Run the init.
        runInit(contractAddress, new avm.Address(extra1.toByteArray()), new avm.Address(extra2.toByteArray()), requiredVotes, dailyLimit);

        // Call "execute" with something above the daily limit so we will create the "Transaction" inner class instance.
        AionAddress to = Helpers.randomAddress();
        byte[] data = Helpers.randomBytes(AionAddress.LENGTH);
        byte[] execArgs = CallEncoder.execute(new avm.Address(to.toByteArray()), dailyLimit + 1, data);
        TestingTransaction executeTransaction = TestingTransaction.call(from, contractAddress, kernel.getNonce(from), BigInteger.ZERO, execArgs, energyLimit, energyPrice);
        TransactionResult executeResult = avm.run(this.kernel, new TestingTransaction[] {executeTransaction})[0].get();
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, executeResult.getResultCode());
        byte[] toConfirm = (byte[]) ABIUtil.decodeOneObject(executeResult.getReturnData());

        // Now, confirm as one of the other owners to observe we can instantiate the Transaction instance, from storage.
        kernel.adjustBalance(extra1, BigInteger.valueOf(1_000_000_000_000L));
        byte[] confirmArgs = CallEncoder.confirm(toConfirm);
        TestingTransaction confirmTransaction = TestingTransaction.call(extra1, contractAddress, kernel.getNonce(extra1), BigInteger.ZERO, confirmArgs, energyLimit, energyPrice);
        TransactionResult confirmResult = avm.run(this.kernel, new TestingTransaction[] {confirmTransaction})[0].get();
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, confirmResult.getResultCode()); // transfer to non-existing accounts
    }


    private void runInit(AionAddress contractAddress, avm.Address extra1, avm.Address extra2, int requiredVotes, long dailyLimit) throws Exception {
        byte[] initArgs = CallEncoder.init(extra1, extra2, requiredVotes, dailyLimit);
        TestingTransaction initTransaction = TestingTransaction.call(from, contractAddress, kernel.getNonce(from), BigInteger.ZERO, initArgs, energyLimit, energyPrice);
        TransactionResult initResult = avm.run(this.kernel, new TestingTransaction[] {initTransaction})[0].get();
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, initResult.getResultCode());
    }

    private byte[] deployTestWallet() {
        byte[] testWalletJar = buildTestWalletJar();
        byte[] testWalletArguments = new byte[0];

        TestingTransaction createTransaction = TestingTransaction.create(from, kernel.getNonce(from), BigInteger.ZERO, new CodeAndArguments(testWalletJar, testWalletArguments).encodeToBytes(), energyLimit, energyPrice);
        TransactionResult createResult = avm.run(this.kernel, new TestingTransaction[] {createTransaction})[0].get();
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, createResult.getResultCode());

        // contract address is stored in return data
        byte[] contractAddress = createResult.getReturnData();
        return contractAddress;
    }
}
