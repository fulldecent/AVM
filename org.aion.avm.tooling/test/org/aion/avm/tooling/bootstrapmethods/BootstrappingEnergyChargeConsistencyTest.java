package org.aion.avm.tooling.bootstrapmethods;

import org.aion.avm.core.util.ABIUtil;
import avm.Address;
import org.aion.avm.tooling.AvmRule;
import org.aion.kernel.AvmTransactionResult;
import org.aion.kernel.AvmTransactionResult.Code;
import org.junit.ClassRule;
import org.junit.Test;

import java.math.BigInteger;

import static org.junit.Assert.assertEquals;

/**
 * Tests that a contract that uses string concatenation and lambdas charges the same amount when
 * invoked multiple times.
 */
public class BootstrappingEnergyChargeConsistencyTest {
    @ClassRule
    public static AvmRule avmRule = new AvmRule(false);

    private long energyLimit = 6_000_000;
    private Address deployer = avmRule.getPreminedAccount();



    @Test
    public void testConsistencyOverMultipleInvocations() {
        // Deploy the contract.
        Address contractAddress = deployContract();

        // Run the contract multiple times.
        AvmTransactionResult result1 = (AvmTransactionResult) runContract(deployer, contractAddress);
        AvmTransactionResult result2 = (AvmTransactionResult) runContract(deployer, contractAddress);
        AvmTransactionResult result3 = (AvmTransactionResult) runContract(deployer, contractAddress);

        // Ensure the calls were all successful.
        assertEquals(Code.SUCCESS, result1.getResultCode());
        assertEquals(Code.SUCCESS, result2.getResultCode());
        assertEquals(Code.SUCCESS, result3.getResultCode());

        // Compare the energy charges.
        long energy1 = result1.getEnergyUsed();
        long energy2 = result2.getEnergyUsed();
        long energy3 = result3.getEnergyUsed();

        assertEquals(energy1, energy2);
        assertEquals(energy2, energy3);

        // The remaining energy amounts should all be equal to one another too.
        long remaining1 = result1.getEnergyRemaining();
        long remaining2 = result2.getEnergyRemaining();
        long remaining3 = result3.getEnergyRemaining();

        assertEquals(remaining1, remaining2);
        assertEquals(remaining2, remaining3);

        // We should also have: energyLimit = energyUsed + energyRemaining
        assertEquals(energyLimit, energy1 + remaining1);
    }

    private Address deployContract() {
        return avmRule.deploy(deployer, BigInteger.ZERO, avmRule.getDappBytes(EnergyChargeConsistencyTarget.class, null)).getDappAddress();
    }

    private AvmTransactionResult runContract(Address sender, Address contract) {
        byte[] callData = ABIUtil.encodeMethodArguments("run");
        return avmRule.call(sender, contract, BigInteger.ZERO, callData, energyLimit, 1L).getTransactionResult();
    }

}
