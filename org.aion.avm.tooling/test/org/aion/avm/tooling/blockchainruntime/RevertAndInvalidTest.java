package org.aion.avm.tooling.blockchainruntime;

import avm.Address;
import org.aion.types.AionAddress;
import org.aion.avm.tooling.AvmRule;
import org.aion.kernel.AvmTransactionResult;
import org.junit.*;

import java.math.BigInteger;

import static org.junit.Assert.*;


public class RevertAndInvalidTest {
    @ClassRule
    public static AvmRule avmRule = new AvmRule(false);

    // transaction
    private static Address deployer = avmRule.getPreminedAccount();
    private static long energyLimit = 1_000_000L;
    private static long energyPrice = 1L;

    private static Address dappAddress;

    @BeforeClass
    public static void setup() {
        dappAddress = deploy();
    }

    private static Address deploy() {
        byte[] arguments = null;
        return avmRule.deploy(deployer, BigInteger.ZERO, avmRule.getDappBytes(RevertAndInvalidTestResource.class, arguments), energyLimit, energyPrice).getDappAddress();
    }

    @Test
    public void testRevert() {
        AvmTransactionResult txResult = (AvmTransactionResult) avmRule.call(deployer, dappAddress, BigInteger.ZERO, new byte[]{1}, energyLimit, energyPrice).getTransactionResult();

        assertEquals(AvmTransactionResult.Code.FAILED_REVERT, txResult.getResultCode());
        assertNull(txResult.getReturnData());
        assertTrue(energyLimit > txResult.getEnergyUsed());
        assertTrue(0 < txResult.getEnergyRemaining());

        // Next hash code is 1 and the value is unchanged at 0.
        assertArrayEquals(new byte[]{0,0,0,1, 0,0,0,0}, avmRule.kernel.getObjectGraph(new AionAddress(dappAddress.toByteArray())));
    }

    @Test
    public void testInvalid() {
        AvmTransactionResult txResult = (AvmTransactionResult) avmRule.call(deployer, dappAddress, BigInteger.ZERO, new byte[]{2}, energyLimit, energyPrice).getTransactionResult();
        assertEquals(AvmTransactionResult.Code.FAILED_INVALID, txResult.getResultCode());
        assertNull(txResult.getReturnData());
        assertEquals(energyLimit, txResult.getEnergyUsed());
        assertEquals(0, txResult.getEnergyRemaining());

        // Next hash code is 1 and the value is unchanged at 0.
        assertArrayEquals(new byte[]{0,0,0,1, 0,0,0,0}, avmRule.kernel.getObjectGraph(new AionAddress(dappAddress.toByteArray())));
    }
}
