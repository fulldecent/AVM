package org.aion.avm.core.blockchainruntime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import avm.Address;
import java.math.BigInteger;
import avm.Blockchain;
import org.aion.types.AionAddress;
import org.aion.avm.core.AvmConfiguration;
import org.aion.avm.core.AvmImpl;
import org.aion.avm.core.CommonAvmFactory;
import org.aion.avm.core.RedirectContract;
import org.aion.avm.core.dappreading.JarBuilder;
import org.aion.avm.core.util.Helpers;
import org.aion.avm.userlib.CodeAndArguments;
import org.aion.avm.userlib.abi.ABIDecoder;
import org.aion.avm.userlib.abi.ABIStreamingEncoder;
import org.aion.kernel.TestingBlock;
import org.aion.kernel.TestingKernel;
import org.aion.kernel.TestingTransaction;
import org.aion.vm.api.interfaces.TransactionResult;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests the {@link Blockchain#getBalanceOfThisContract()} method for retrieving the balance
 * of a deployed contract from within that contract.
 */
public class ContractBalanceTest {
    private static AionAddress from = TestingKernel.PREMINED_ADDRESS;
    private static long energyLimit = 10_000_000L;
    private static long energyPrice = 5;

    private static TestingKernel kernel;
    private static AvmImpl avm;

    @BeforeClass
    public static void setup() {
        TestingBlock block = new TestingBlock(new byte[32], 1, Helpers.randomAddress(), System.currentTimeMillis(), new byte[0]);
        kernel = new TestingKernel(block);
        avm = CommonAvmFactory.buildAvmInstanceForConfiguration(new EmptyCapabilities(), new AvmConfiguration());
    }

    @AfterClass
    public static void tearDown() {
        avm.shutdown();
    }

    @Test
    public void testClinitBalanceWhenTransferringZero() {
        AionAddress contract = deployContract(BigInteger.ZERO);
        BigInteger actualBalance = callContractToGetClinitBalance(contract);
        assertEquals(BigInteger.ZERO, actualBalance);
    }

    @Test
    public void testClinitBalanceWhenTransferringPositiveAmount() {
        BigInteger transferAmount = BigInteger.valueOf(1234567);
        AionAddress contract = deployContract(transferAmount);
        BigInteger actualBalance = callContractToGetClinitBalance(contract);
        assertEquals(transferAmount, actualBalance);
    }

    @Test
    public void testContractBalance() {
        AionAddress contract = deployContract(BigInteger.ZERO);

        // Contract currently has no balance.
        BigInteger balance = callContractToGetItsBalance(contract);
        assertEquals(BigInteger.ZERO, balance);

        // Increase the contract balance and check the amount.
        BigInteger delta1 = BigInteger.TWO.pow(250);
        kernel.adjustBalance(contract, delta1);
        balance = callContractToGetItsBalance(contract);
        assertEquals(delta1, balance);

        // Decrease the contract balance and check the amount.
        BigInteger delta2 = BigInteger.TWO.pow(84).negate();
        kernel.adjustBalance(contract, delta2);
        balance = callContractToGetItsBalance(contract);
        assertEquals(delta1.add(delta2), balance);
    }

    @Test
    public void testContractBalanceViaInternalTransaction() {
        AionAddress balanceContract = deployContract(BigInteger.ZERO);
        AionAddress redirectContract = deployRedirectContract();

        // We give the redirect contract some balance to ensure we aren't querying the wrong contract.
        kernel.adjustBalance(redirectContract, BigInteger.valueOf(2938752));

        // Contract currently has no balance.
        BigInteger balance = callContractToGetItsBalanceViaRedirectContract(redirectContract, balanceContract);
        assertEquals(BigInteger.ZERO, balance);
    }

    /**
     * Deploys the contract and transfers value amount of Aion into it.
     */
    private AionAddress deployContract(BigInteger value) {
        kernel.generateBlock();
        byte[] jar = JarBuilder.buildJarForMainAndClassesAndUserlib(ContractBalanceTarget.class);
        jar = new CodeAndArguments(jar, new byte[0]).encodeToBytes();

        TestingTransaction transaction = TestingTransaction.create(from, kernel.getNonce(from), value, jar, energyLimit, energyPrice);
        TransactionResult result = avm.run(ContractBalanceTest.kernel, new TestingTransaction[] {transaction})[0].get();
        assertTrue(result.getResultCode().isSuccess());
        return new AionAddress(result.getReturnData());
    }

    private BigInteger callContractToGetItsBalance(AionAddress contract) {
        kernel.generateBlock();
        byte[] callData = new ABIStreamingEncoder()
                .encodeOneString("getBalanceOfThisContract")
                .toBytes();
        TestingTransaction transaction = TestingTransaction.call(from, contract, kernel.getNonce(from), BigInteger.ZERO, callData, energyLimit, energyPrice);
        TransactionResult result = avm.run(ContractBalanceTest.kernel, new TestingTransaction[] {transaction})[0].get();
        assertTrue(result.getResultCode().isSuccess());
        return new BigInteger(new ABIDecoder(result.getReturnData()).decodeOneByteArray());
    }

    private BigInteger callContractToGetClinitBalance(AionAddress contract) {
        kernel.generateBlock();
        byte[] callData = new ABIStreamingEncoder()
                .encodeOneString("getBalanceOfThisContractDuringClinit")
                .toBytes();
        TestingTransaction transaction = TestingTransaction.call(from, contract, kernel.getNonce(from), BigInteger.ZERO, callData, energyLimit, energyPrice);
        TransactionResult result = avm.run(ContractBalanceTest.kernel, new TestingTransaction[] {transaction})[0].get();
        assertTrue(result.getResultCode().isSuccess());
        return new BigInteger(new ABIDecoder(result.getReturnData()).decodeOneByteArray());
    }

    private AionAddress deployRedirectContract() {
        kernel.generateBlock();
        byte[] jar = JarBuilder.buildJarForMainAndClassesAndUserlib(RedirectContract.class);
        jar = new CodeAndArguments(jar, new byte[0]).encodeToBytes();

        TestingTransaction transaction = TestingTransaction.create(from, kernel.getNonce(from), BigInteger.ZERO, jar, energyLimit, energyPrice);
        TransactionResult result = avm.run(ContractBalanceTest.kernel, new TestingTransaction[] {transaction})[0].get();
        assertTrue(result.getResultCode().isSuccess());
        return new AionAddress(result.getReturnData());
    }

    private BigInteger callContractToGetItsBalanceViaRedirectContract(AionAddress redirectContract, AionAddress balanceContract) {
        Address contract = getContractAsAbiAddress(balanceContract);
        byte[] args = new ABIStreamingEncoder()
                .encodeOneString("getBalanceOfThisContract")
                .toBytes();
        byte[] callData = new ABIStreamingEncoder()
                .encodeOneString("callOtherContractAndRequireItIsSuccess")
                .encodeOneAddress(contract)
                .encodeOneLong(0L)
                .encodeOneByteArray(args)
                .toBytes();
        return runTransactionAndInterpretOutputAsBigInteger(redirectContract, callData);
    }

    private BigInteger runTransactionAndInterpretOutputAsBigInteger(AionAddress contract, byte[] callData) {
        kernel.generateBlock();
        TestingTransaction transaction = TestingTransaction.call(from, contract, kernel.getNonce(from), BigInteger.ZERO, callData, energyLimit, energyPrice);
        TransactionResult result = avm.run(ContractBalanceTest.kernel, new TestingTransaction[] {transaction})[0].get();
        assertTrue(result.getResultCode().isSuccess());
        return new BigInteger(new ABIDecoder(result.getReturnData()).decodeOneByteArray());
    }

    private Address getContractAsAbiAddress(AionAddress contract) {
        return new Address(contract.toByteArray());
    }

}
