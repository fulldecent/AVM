package org.aion.avm.core;

import avm.Address;
import org.aion.avm.core.blockchainruntime.EmptyCapabilities;
import org.aion.avm.core.dappreading.JarBuilder;
import org.aion.avm.core.util.CodeAndArguments;
import org.aion.avm.core.util.ABIUtil;
import org.aion.avm.core.util.Helpers;
import org.aion.kernel.*;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;

import static org.junit.Assert.assertTrue;

/**
 * Difference of energy consumption explained more in issue-345
 */
public class EnergyUsageDebugModeTest {
    private TestingBlock block;
    private TestingKernel kernel;
    private org.aion.types.Address deployer = TestingKernel.PREMINED_ADDRESS;


    @Before
    public void setup (){
        block = new TestingBlock(new byte[32], 1, Helpers.randomAddress(), System.currentTimeMillis(), new byte[0]);
        this.kernel = new TestingKernel(block);
    }

    /**
     * since the debug mode's code blocks are smaller than the normal mode, the energy consumed after throwing an exception (in debug mode) is less
     */
    @Test
    public void testEnergyConsumptionDivisionFunction(){
        long debugEnergyUsageDivision = testEnergyUsedInDebugMode("tryToDivideInteger", 10, 0);
        long normalEnergyUsageDivision = testEnergyUsedInNormalMode("tryToDivideInteger", 10, 0);
        assertTrue(debugEnergyUsageDivision < normalEnergyUsageDivision);
    }

    /**
     * since the debug mode's code blocks are smaller, only what is executed will be billed.
     * Thus the faster the exception is thrown, the lower the energy consumption.
     */
    @Test
    public void testEnergyConsumptionInDebug(){
        long debugEnergyUsageFailEarly = testEnergyUsedInDebugMode("tryToDivideInteger", 10, 0);
        long debugEnergyUsageFailLate = testEnergyUsedInDebugMode("tryToDivideInteger", 0, 10);

        assertTrue(debugEnergyUsageFailEarly < debugEnergyUsageFailLate);
    }

    private long testEnergyUsedInDebugMode(String methodName, Object ... args){
        AvmConfiguration config = new AvmConfiguration();
        config.preserveDebuggability = true;
        config.enableVerboseContractErrors = true;
        AvmImpl avmDebugMode = CommonAvmFactory.buildAvmInstanceForConfiguration(new EmptyCapabilities(), config);

        byte[] jar = JarBuilder.buildJarForMainAndClassesAndUserlib(EnergyUsageDebugModeTarget.class);
        byte[] txData = new CodeAndArguments(jar, new byte[0]).encodeToBytes();
        long energyPrice = 1l;

        //deploy in debugMode Mode
        TestingTransaction create = TestingTransaction.create(deployer, kernel.getNonce(deployer), BigInteger.ZERO, txData, 10_000_000l, energyPrice);
        AvmTransactionResult createResult = avmDebugMode.run(this.kernel, new TestingTransaction[] {create})[0].get();
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, createResult.getResultCode());
        Address contractAddressDebug = new Address(createResult.getReturnData());

        long energyLimit = 1_000_000l;
        byte[] argData = ABIUtil.encodeMethodArguments(methodName, args);
        TestingTransaction call = TestingTransaction.call(deployer, org.aion.types.Address.wrap(contractAddressDebug.toByteArray()), kernel.getNonce(deployer), BigInteger.ZERO, argData, energyLimit, 1l);
        AvmTransactionResult result = avmDebugMode.run(this.kernel, new TestingTransaction[] {call})[0].get();

        long energyUsed = energyLimit - result.getEnergyRemaining();
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, result.getResultCode());
        Assert.assertEquals(111, new BigInteger(result.getReturnData()).intValue());

        avmDebugMode.shutdown();
        return energyUsed;
    }

    private long testEnergyUsedInNormalMode(String methodName, Object ... args){

        AvmImpl avmNormalMode = CommonAvmFactory.buildAvmInstanceForConfiguration(new EmptyCapabilities(), new AvmConfiguration());
        byte[] jar = JarBuilder.buildJarForMainAndClassesAndUserlib(EnergyUsageDebugModeTarget.class);
        byte[] txData = new CodeAndArguments(jar, new byte[0]).encodeToBytes();
        long energyLimit = 1_000_000l;
        long energyPrice = 1l;

        //deploy in normal Mode
        TestingTransaction create = TestingTransaction.create(deployer, kernel.getNonce(deployer), BigInteger.ZERO, txData, 10_000_000l, energyPrice);
        AvmTransactionResult createResult = avmNormalMode.run(this.kernel, new TestingTransaction[] {create})[0].get();
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, createResult.getResultCode());
        Address contractAddressNormal = new Address(createResult.getReturnData());

        byte[] argData = ABIUtil.encodeMethodArguments(methodName, args);
        TestingTransaction call = TestingTransaction.call(deployer, org.aion.types.Address.wrap(contractAddressNormal.toByteArray()), kernel.getNonce(deployer), BigInteger.ZERO, argData, energyLimit, 1l);
        AvmTransactionResult result = avmNormalMode.run(this.kernel, new TestingTransaction[] {call})[0].get();
        long energyUsed = energyLimit - result.getEnergyRemaining();
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, result.getResultCode());
        Assert.assertEquals(111, new BigInteger(result.getReturnData()).intValue());
        avmNormalMode.shutdown();
        return energyUsed;
    }
}
