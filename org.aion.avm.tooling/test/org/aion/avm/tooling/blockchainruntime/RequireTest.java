package org.aion.avm.tooling.blockchainruntime;

import avm.Blockchain;
import org.aion.avm.core.util.ABIUtil;
import org.aion.avm.userlib.abi.ABIEncoder;
import avm.Address;
import org.aion.avm.tooling.AvmRule;
import org.aion.avm.tooling.RedirectContract;
import org.aion.kernel.AvmTransactionResult;
import org.aion.kernel.AvmTransactionResult.Code;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.math.BigInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests the {@link Blockchain#require(boolean)} method.
 */
public class RequireTest {
    @ClassRule
    public static AvmRule avmRule = new AvmRule(false);

    private static Address from = avmRule.getPreminedAccount();
    private static long energyLimit = 10_000_000L;
    private static long energyPrice = 5;
    private static Address contract;

    @BeforeClass
    public static void setup() {
        deployContract();
    }

    @Test
    public void testRequireOnTrueCondition() {
        AvmTransactionResult result = callContractRequireMethod(true);
        assertTrue(result.getResultCode().isSuccess());
    }

    @Test
    public void testRequireOnFalseCondition() {
        AvmTransactionResult result = callContractRequireMethod(false);
        assertEquals(Code.FAILED_REVERT, result.getResultCode());

        // A REVERT should NOT use up all remaining energy. We should be refunded.
        assertTrue(result.getEnergyRemaining() > 0);
    }

    @Test
    public void testRequireOnTrueConditionOnInternalCondition() {
        // We use the RedirectContract to trigger an internal transaction into the RequireTarget contract.
        Address redirectContract = deployRedirectContract();
        AvmTransactionResult result = callRedirectContract(redirectContract, true);

        // If redirect condition is SUCCESS then its internal call was also SUCCESS.
        assertTrue(result.getResultCode().isSuccess());
    }

    @Test
    public void testRequireOnFalseConditionOnInternalCondition() {
        // We use the RedirectContract to trigger an internal transaction into the RequireTarget contract.
        Address redirectContract = deployRedirectContract();
        AvmTransactionResult result = callRedirectContract(redirectContract, false);

        // If internal call was not SUCCESS then redirect gets a REVERT as well.
        assertEquals(Code.FAILED_REVERT, result.getResultCode());
    }

    @Test
    public void testUnableToCatchRevertExceptionFromRequire() {
        assertEquals(Code.FAILED_REVERT, callContractRequireAndAttemptToCatchExceptionMethod().getResultCode());
    }

    @Test
    public void testRequireInClinitOnTrueCondition() {
        assertTrue(deployContractAndTriggerClinitRequire(true).getResultCode().isSuccess());
    }

    @Test
    public void testRequireInClinitOnFalseCondition() {
        assertEquals(Code.FAILED_REVERT, deployContractAndTriggerClinitRequire(false).getResultCode());
    }

    private static AvmTransactionResult deployContractAndTriggerClinitRequire(boolean condition) {
        byte[] clinitData = ABIEncoder.encodeOneBoolean(condition);
        byte[] data = getRawJarBytesForRequireContract(clinitData);
        return avmRule.deploy(from, BigInteger.ZERO, data, energyLimit, energyPrice).getTransactionResult();
    }

    private static void deployContract() {
        byte[] jar = getRawJarBytesForRequireContract(new byte[0]);
        AvmTransactionResult result = avmRule.deploy(from, BigInteger.ZERO, jar, energyLimit, energyPrice).getTransactionResult();
        assertTrue(result.getResultCode().isSuccess());
        contract = new Address(result.getReturnData());
    }

    private AvmTransactionResult callContractRequireMethod(boolean condition) {
        byte[] callData = getAbiEncodingOfRequireContractCall(condition);
        return avmRule.call(from, contract, BigInteger.ZERO, callData, energyLimit, energyPrice).getTransactionResult();
    }

    private AvmTransactionResult callContractRequireAndAttemptToCatchExceptionMethod() {
        byte[] callData = ABIUtil.encodeMethodArguments("requireAndTryToCatch");
        return avmRule.call(from, contract, BigInteger.ZERO, callData, energyLimit, energyPrice).getTransactionResult();
    }

    private static Address deployRedirectContract() {
        byte[] jar = avmRule.getDappBytes(RedirectContract.class, new byte[0]);

        AvmTransactionResult result = avmRule.deploy(from, BigInteger.ZERO, jar, energyLimit, energyPrice).getTransactionResult();
        assertTrue(result.getResultCode().isSuccess());
        return new Address(result.getReturnData());
    }

    private AvmTransactionResult callRedirectContract(Address redirect, boolean condition) {
        byte[] callData = encodeRedirectCallArgs(condition);
        return avmRule.call(from, redirect, BigInteger.ZERO, callData, energyLimit, energyPrice).getTransactionResult();
    }

    private byte[] encodeRedirectCallArgs(boolean condition) {
        byte[] args = getAbiEncodingOfRequireContractCall(condition);
        return ABIUtil.encodeMethodArguments("callOtherContractAndRequireItIsSuccess", contract, 0L, args);
    }

    private byte[] getAbiEncodingOfRequireContractCall(boolean condition) {
        return ABIUtil.encodeMethodArguments("require", condition);
    }

    private static byte[] getRawJarBytesForRequireContract(byte[] args) {
        return avmRule.getDappBytes(RequireTarget.class, args);
    }

}
