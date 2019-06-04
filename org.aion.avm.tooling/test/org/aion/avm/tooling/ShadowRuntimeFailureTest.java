package org.aion.avm.tooling;

import org.aion.avm.core.dappreading.JarBuilder;
import org.aion.avm.core.util.ABIUtil;
import org.aion.avm.core.util.CodeAndArguments;
import org.aion.avm.tooling.deploy.JarOptimizer;
import avm.Address;
import org.aion.kernel.AvmTransactionResult;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import java.math.BigInteger;


/**
 * Verifies that shadow box type constructors cannot be called.
 */
public class ShadowRuntimeFailureTest {
    private boolean preserveDebugInfo = false;

    @Rule
    public AvmRule avmRule = new AvmRule(preserveDebugInfo);

    private static final long ENERGY_LIMIT = 10_000_000L;
    private static final long ENERGY_PRICE = 1L;

    Address deployer = avmRule.getPreminedAccount();

    @Test
    public void testFailuresInDeployment() {
        // 0-7 are failures and 8 is a success.
        for (int i = 0; i < 8; ++i) {
            byte[] data = getDappBytesWithUserlib(ShadowRuntimeFailureTarget.class, new byte[] {(byte)i});
            
            // deploy
            AvmTransactionResult result = avmRule.deploy(deployer, BigInteger.ZERO, data, ENERGY_LIMIT, ENERGY_PRICE).getTransactionResult();
            Assert.assertEquals(AvmTransactionResult.Code.FAILED_EXCEPTION, result.getResultCode());
        }
        byte[] data = getDappBytesWithUserlib(ShadowRuntimeFailureTarget.class, new byte[] {(byte)8});
        AvmTransactionResult result = avmRule.deploy(deployer, BigInteger.ZERO, data, ENERGY_LIMIT, ENERGY_PRICE).getTransactionResult();
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, result.getResultCode());
    }

    @Test
    public void testFailuresInCall() {
        byte[] txData = getDappBytesWithUserlib(ShadowRuntimeFailureTarget.class, new byte[0]);
        AvmTransactionResult result1 = avmRule.deploy(deployer, BigInteger.ZERO, txData, ENERGY_LIMIT, ENERGY_PRICE).getTransactionResult();
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, result1.getResultCode());
        Address contractAddr = new Address(result1.getReturnData());
        
        // 0-7 are failures and 8 is a success.
        for (int i = 0; i < 8; ++i) {
            byte[] data =  new byte[] {(byte)i};
            AvmTransactionResult result  = avmRule.call(deployer, contractAddr, BigInteger.ZERO, data, ENERGY_LIMIT, ENERGY_PRICE).getTransactionResult();
            Assert.assertEquals(AvmTransactionResult.Code.FAILED_EXCEPTION, result.getResultCode());
       }
        byte[] data = new byte[] {(byte)8};
        AvmTransactionResult result  = avmRule.call(deployer, contractAddr, BigInteger.ZERO, data, ENERGY_LIMIT, ENERGY_PRICE).getTransactionResult();
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, result.getResultCode());
        Assert.assertEquals(Boolean.valueOf(true), ABIUtil.decodeOneObject(result.getReturnData()));
        
    }

    private byte[] getDappBytesWithUserlib(Class<?> mainClass, byte[] arguments, Class<?>... otherClasses) {
        JarOptimizer jarOptimizer = new JarOptimizer(preserveDebugInfo);
        byte[] jar = JarBuilder.buildJarForMainAndClassesAndUserlib(mainClass, otherClasses);
        byte[] optimizedDappBytes = jarOptimizer.optimize(jar);
        return new CodeAndArguments(optimizedDappBytes, arguments).encodeToBytes();
    }
}
