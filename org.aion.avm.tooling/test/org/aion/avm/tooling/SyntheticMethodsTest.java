package org.aion.avm.tooling;

import org.aion.avm.core.util.ABIUtil;
import avm.Address;
import org.aion.avm.userlib.AionList;
import org.aion.avm.userlib.AionMap;
import org.aion.avm.userlib.AionSet;
import org.aion.kernel.AvmTransactionResult;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.math.BigInteger;

/**
 * As part of issue-215, we want to see if Synthetic Methods would break any of our assumptions in method
 * invocation. The Java compiler will generate two methods in the bytecode: one takes the Obejct as argument,
 * the other takes the specific type as argument. This test operates on SyntheticMethodsTestTarget to observe
 * any possible issues when we have a concrete method that overrides a generic method.
 */
public class SyntheticMethodsTest {
    @Rule
    public AvmRule avmRule = new AvmRule(false);

    private Address from = avmRule.getPreminedAccount();
    private Address dappAddr;

    private long energyLimit = 6_000_0000;
    private long energyPrice = 1;


    @Before
    public void setup() {
        byte[] txData = avmRule.getDappBytes(SyntheticMethodsTestTarget.class, null, AionMap.class, AionSet.class, AionList.class);
        dappAddr = avmRule.deploy(from, BigInteger.ZERO, txData, energyLimit, energyPrice).getDappAddress();
    }

    @Test
    public void testDappWorking() {
        AvmTransactionResult result = createAndRunTransaction("getCompareResult");

        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, result.getResultCode());
        Assert.assertEquals(SyntheticMethodsTestTarget.DEFAULT_VALUE, ABIUtil.decodeOneObject(result.getReturnData()));
    }

    @Test
    public void testCompareTo() {
        // BigInteger
        AvmTransactionResult result1 = createAndRunTransaction("compareSomething", 1);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, result1.getResultCode());
        AvmTransactionResult result1Value = createAndRunTransaction("getCompareResult");
        Assert.assertEquals(1, ABIUtil.decodeOneObject(result1Value.getReturnData()));

        AvmTransactionResult result2 = createAndRunTransaction("compareSomething", 2);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, result2.getResultCode());
        AvmTransactionResult result2Value = createAndRunTransaction("getCompareResult");
        Assert.assertEquals(0, ABIUtil.decodeOneObject(result2Value.getReturnData()));

        AvmTransactionResult result3 = createAndRunTransaction("compareSomething", 3);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, result3.getResultCode());
        AvmTransactionResult result3Value = createAndRunTransaction("getCompareResult");
        Assert.assertEquals(-1, ABIUtil.decodeOneObject(result3Value.getReturnData()));

        AvmTransactionResult result4 = createAndRunTransaction("compareSomething", 4);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, result4.getResultCode());
        AvmTransactionResult result4Value = createAndRunTransaction("getCompareResult");
        Assert.assertEquals(100, ABIUtil.decodeOneObject(result4Value.getReturnData()));
    }

    @Test
    public void testTarget(){
        // pick target1Impl
        AvmTransactionResult result1 = createAndRunTransaction("pickTarget", 1);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, result1.getResultCode());

        // check for correctness in synthetic, should get impl1 name
        AvmTransactionResult result2 = createAndRunTransaction("getName");
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, result2.getResultCode());
        Assert.assertEquals("TargetClassImplOne", ABIUtil.decodeOneObject(result2.getReturnData()));

        // pick target2Impl
        AvmTransactionResult result3 = createAndRunTransaction("pickTarget", 2);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, result3.getResultCode());

        // check for correctness in synthetic, should get abstract name
        AvmTransactionResult result4 = createAndRunTransaction("getName");
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, result4.getResultCode());
        Assert.assertEquals("TargetAbstractClass", ABIUtil.decodeOneObject(result4.getReturnData()));
    }

    @Test
    public void testGenericMethodOverride(){
        int inputGeneric = 10;
        int inputOverrideGeneric = 20;

        // calling setup generics
        AvmTransactionResult result1 = createAndRunTransaction("setGenerics",
                inputGeneric, inputOverrideGeneric);
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, result1.getResultCode());

        // retrieve each object
        AvmTransactionResult result2 = createAndRunTransaction("getIntGen");
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, result2.getResultCode());
        Assert.assertEquals(inputGeneric, ABIUtil.decodeOneObject(result2.getReturnData()));

        AvmTransactionResult result3 = createAndRunTransaction("getIntGenSub");
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, result3.getResultCode());
        Assert.assertEquals(inputOverrideGeneric, ABIUtil.decodeOneObject(result3.getReturnData()));

        AvmTransactionResult result4 = createAndRunTransaction("getSubCopy");
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, result4.getResultCode());
        Assert.assertEquals(inputOverrideGeneric, ABIUtil.decodeOneObject(result4.getReturnData()));
    }

    private AvmTransactionResult createAndRunTransaction(String methodName, Object ... args){
        byte[] txData = ABIUtil.encodeMethodArguments(methodName, args);
        return avmRule.call(from, dappAddr, BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();
    }
}
