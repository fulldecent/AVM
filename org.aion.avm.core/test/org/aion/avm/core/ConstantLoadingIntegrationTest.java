package org.aion.avm.core;

import java.math.BigInteger;

import org.aion.types.AionAddress;
import org.aion.avm.core.blockchainruntime.EmptyCapabilities;
import org.aion.avm.core.dappreading.JarBuilder;
import org.aion.avm.core.util.CodeAndArguments;
import org.aion.avm.core.util.Helpers;
import org.aion.kernel.AvmTransactionResult;
import org.aion.kernel.TestingBlock;
import org.aion.kernel.TestingKernel;
import org.aion.kernel.TestingTransaction;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;


/**
 * Tests that we can do things like default methods with constants in the interface, etc, to prove
 * that constants are being correctly loaded, and can be referenced from, the constant class.
 */
public class ConstantLoadingIntegrationTest {
    private AionAddress deployer = TestingKernel.PREMINED_ADDRESS;
    private TestingKernel kernel;
    private AvmImpl avm;

    TestingBlock block;

    @Before
    public void setup() {
        block = new TestingBlock(new byte[32], 1, Helpers.randomAddress(), System.currentTimeMillis(), new byte[0]);
        this.kernel = new TestingKernel(block);
        this.avm = CommonAvmFactory.buildAvmInstanceForConfiguration(new EmptyCapabilities(), new AvmConfiguration());
    }

    @After
    public void tearDown() {
        this.avm.shutdown();
    }

    @Test
    public void testCreation() throws Exception {
        AionAddress contractAddr = deploy();
        
        // Test just the creation modes.
        int bareHash = 59;
        int bareLength = 6;
        int populateHash = 62;
        int populateLength = 3;
        byte[] bare = callStatic(block, contractAddr, 0);
        byte[] populated = callStatic(block, contractAddr, 1);
        
        Assert.assertEquals((byte)bareHash, bare[0]);
        Assert.assertEquals((byte)bareLength, bare[1]);
        Assert.assertEquals((byte)populateHash, populated[0]);
        Assert.assertEquals((byte)populateLength, populated[1]);
    }

    @Test
    public void testPersistence() throws Exception {
        AionAddress contractAddr = deploy();
        
        // Run the creation and then test the read calls.
        callStatic(block, contractAddr, 0);
        callStatic(block, contractAddr, 1);
        
        int bareHash = 59;
        int bareLength = 6;
        int populateHash = 62;
        int populateLength = 3;
        byte[] bare = callStatic(block, contractAddr, 2);
        byte[] populated = callStatic(block, contractAddr, 3);
        
        Assert.assertEquals((byte)bareHash, bare[0]);
        Assert.assertEquals((byte)bareLength, bare[1]);
        Assert.assertEquals((byte)populateHash, populated[0]);
        Assert.assertEquals((byte)populateLength, populated[1]);
    }


    private AionAddress deploy() {
        byte[] jar = JarBuilder.buildJarForMainAndClassesAndUserlib(ConstantLoadingIntegrationTestTarget.class);
        byte[] txData = new CodeAndArguments(jar, new byte[0]).encodeToBytes();
        
        // Deploy.
        long energyLimit = 10_000_000l;
        long energyPrice = 1l;
        TestingTransaction create = TestingTransaction.create(deployer, kernel.getNonce(
            deployer), BigInteger.ZERO, txData, energyLimit, energyPrice);
        AvmTransactionResult createResult = (AvmTransactionResult) avm.run(this.kernel, new TestingTransaction[] {create})[0].get();
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, createResult.getResultCode());

        return new AionAddress(createResult.getReturnData());
    }

    private byte[] callStatic(TestingBlock block, AionAddress contractAddr, int code) {
        long energyLimit = 1_000_000l;
        byte[] argData = new byte[] { (byte)code };
        TestingTransaction call = TestingTransaction.call(deployer, contractAddr, kernel.getNonce(deployer), BigInteger.ZERO, argData, energyLimit, 1l);
        AvmTransactionResult result = (AvmTransactionResult) avm.run(this.kernel, new TestingTransaction[] {call})[0].get();
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, result.getResultCode());
        return result.getReturnData();
    }
}
