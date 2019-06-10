package org.aion.avm.core.shadowing.lambdas;

import java.math.BigInteger;

import org.aion.types.AionAddress;
import org.aion.avm.core.AvmConfiguration;
import org.aion.avm.core.AvmImpl;
import org.aion.avm.core.CommonAvmFactory;
import org.aion.avm.core.blockchainruntime.EmptyCapabilities;
import org.aion.avm.core.dappreading.JarBuilder;
import org.aion.avm.core.util.CodeAndArguments;
import org.aion.avm.core.util.Helpers;
import org.aion.kernel.*;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;


public class FunctionShadowTest {
    private static final long ENERGY_LIMIT = 6_000_000L;
    private static final long ERNGY_PRICE = 1L;
    private static final AionAddress FROM = TestingKernel.PREMINED_ADDRESS;

    private TestingBlock block;
    private TestingKernel kernel;
    private AvmImpl avm;

    @Before
    public void setup() {
        this.block = new TestingBlock(new byte[32], 1, Helpers.randomAddress(), System.currentTimeMillis(), new byte[0]);
        this.kernel = new TestingKernel(this.block);
        AvmConfiguration config = new AvmConfiguration();
        config.enableVerboseContractErrors = true;
        config.preserveDebuggability = true;
        this.avm = CommonAvmFactory.buildAvmInstanceForConfiguration(new EmptyCapabilities(), config);
    }

    @After
    public void tearDown() {
        this.avm.shutdown();
    }

    @Test
    public void testNonWhitelistFunction() {
        Class<?> testClass = FunctionShadowFailSupplierResource.class;
        AionAddress dappAddr = deployTest(testClass);
        // We expect a deployment failure.
        Assert.assertNull(dappAddr);
    }

    @Test
    public void testParameterFunction() {
        Class<?> testClass = FunctionShadowFailArgsResource.class;
        AionAddress dappAddr = deployTest(testClass);
        // We expect a deployment failure.
        Assert.assertNull(dappAddr);
    }

    @Test
    public void testSafeParameterFunction() {
        Class<?> testClass = FunctionShadowPassArgsResource.class;
        AionAddress dappAddr = deployTest(testClass);
        // This case should succeed.
        Assert.assertNotNull(dappAddr);
    }

    @Test
    public void testNoComparableFunction() {
        Class<?> testClass = FunctionShadowFailComparableResource.class;
        AionAddress dappAddr = deployTest(testClass);
        // We expect a deployment failure.
        Assert.assertNull(dappAddr);
    }

    @Test
    public void testLambdaRunnable() {
        // deploy it
        Class<?> testClass = FunctionShadowResource.class;
        AionAddress dappAddr = deployTest(testClass);
        
        // call transactions and validate the results
        oneCall(dappAddr, 0);
    }

    @Test
    public void testLambdaFunction() {
        // deploy it
        Class<?> testClass = FunctionShadowResource.class;
        AionAddress dappAddr = deployTest(testClass);
        
        // call transactions and validate the results
        oneCall(dappAddr, 1);
    }

    @Test
    public void testSerializedLambdaRunnable() {
        // deploy it
        Class<?> testClass = FunctionShadowResource.class;
        AionAddress dappAddr = deployTest(testClass);
        
        // Call the setup routine (2)
        oneCall(dappAddr, 2);
        
        // Call the check routine (3)
        oneCall(dappAddr, 3);
    }

    @Test
    public void testSerializedLambdaFunction() {
        // deploy it
        Class<?> testClass = FunctionShadowResource.class;
        AionAddress dappAddr = deployTest(testClass);
        
        // Call the setup routine (4)
        oneCall(dappAddr, 4);
        
        // Call the check routine (5)
        oneCall(dappAddr, 5);
    }

    @Test
    public void testReferenceFunction() {
        // deploy it
        Class<?> testClass = FunctionShadowResource.class;
        AionAddress dappAddr = deployTest(testClass);
        
        // call transactions and validate the results
        oneCall(dappAddr, 6);
    }

    @Test
    public void testNonSharedLambdas() {
        // deploy it
        Class<?> testClass = FunctionShadowResource.class;
        AionAddress dappAddr = deployTest(testClass);
        
        // call transactions and validate the results
        oneCall(dappAddr, 7);
    }

    @Test
    public void testExceptionInLambdas() {
        // deploy it
        Class<?> testClass = FunctionShadowResource.class;
        AionAddress dappAddr = deployTest(testClass);
        
        // call transactions and validate the results
        oneCall(dappAddr, 8);
    }


    private AionAddress deployTest(Class<?> testClass) {
        byte[] testJar = JarBuilder.buildJarForMainAndClassesAndUserlib(testClass);
        byte[] txData = new CodeAndArguments(testJar, null).encodeToBytes();
        TestingTransaction tx = TestingTransaction.create(FROM, this.kernel.getNonce(FROM), BigInteger.ZERO, txData, ENERGY_LIMIT, ERNGY_PRICE);
        byte[] returnData = this.avm.run(this.kernel, new TestingTransaction[] {tx})[0].get().getReturnData();
        return (null != returnData)
                ? new AionAddress(returnData)
                : null;
    }

    private void oneCall(AionAddress dappAddr, int transactionNumber) {
        TestingTransaction tx = TestingTransaction.call(FROM, dappAddr, this.kernel.getNonce(FROM), BigInteger.ZERO, new byte[] {(byte)transactionNumber}, ENERGY_LIMIT, ERNGY_PRICE);
        AvmTransactionResult result = (AvmTransactionResult) this.avm.run(this.kernel, new TestingTransaction[] {tx})[0].get();
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, result.getResultCode());
    }
}
