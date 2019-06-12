package org.aion.avm.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import org.aion.types.AionAddress;
import org.aion.avm.core.blockchainruntime.EmptyCapabilities;
import org.aion.avm.core.dappreading.JarBuilder;
import org.aion.avm.core.util.Helpers;
import org.aion.avm.userlib.CodeAndArguments;
import org.aion.avm.userlib.abi.ABIStreamingEncoder;
import org.aion.kernel.AvmTransactionResult;
import org.aion.kernel.TestingBlock;
import org.aion.kernel.TestingKernel;
import org.aion.kernel.TestingTransaction;
import org.aion.vm.api.interfaces.KernelInterface;
import org.aion.vm.api.interfaces.TransactionResult;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests that key players related to a transaction have their account balances updated correctly
 * after a transaction has been sent.
 */
public class TransactionAccountBalanceTest {
    private static AionAddress from = TestingKernel.PREMINED_ADDRESS;
    private static long energyLimit = 10_000_000L;

    private static long energyLimitForValueTransfer = 21_000L;
    private static long energyPrice = 5;
    private static TestingBlock block = new TestingBlock(new byte[32], 1, Helpers.randomAddress(), System.currentTimeMillis(), new byte[0]);

    private static TestingKernel kernel;
    private static AvmImpl avm;

    @BeforeClass
    public static void setup() {
        kernel = new TestingKernel(block);
        avm = CommonAvmFactory.buildAvmInstanceForConfiguration(new EmptyCapabilities(), new AvmConfiguration());
    }

    @AfterClass
    public static void tearDown() {
        avm.shutdown();
    }

    @Test
    public void testSenderBalanceAfterCreateNoValueSent() {
        BigInteger senderBalance = kernel.getBalance(from);

        TransactionResult result = deployContract(BigInteger.ZERO);
        long energyUsed = ((AvmTransactionResult) result).getEnergyUsed();
        assertTrue(energyUsed > 0);
        assertEquals(energyLimit, energyUsed + result.getEnergyRemaining());

        BigInteger transactionCost = BigInteger.valueOf(energyUsed * energyPrice);
        assertEquals(senderBalance.subtract(transactionCost), kernel.getBalance(from));
    }

    @Test
    public void testSenderBalanceAfterCreateValueSent() {
        BigInteger value = new BigInteger("328756");
        BigInteger senderBalance = kernel.getBalance(from);

        TransactionResult result = deployContract(value);
        long energyUsed = ((AvmTransactionResult) result).getEnergyUsed();
        assertTrue(energyUsed > 0);
        assertEquals(energyLimit, energyUsed + result.getEnergyRemaining());

        BigInteger transactionCost = BigInteger.valueOf(energyUsed * energyPrice);
        assertEquals(senderBalance.subtract(transactionCost).subtract(value), kernel.getBalance(from));
    }

    @Test
    public void testSenderBalanceAfterCallNoValueSent() {
        AionAddress contract = deployContractAndGetAddress();

        BigInteger senderBalance = kernel.getBalance(from);

        TransactionResult result = callContract(contract, BigInteger.ZERO);
        long energyUsed = ((AvmTransactionResult) result).getEnergyUsed();
        assertTrue(energyUsed > 0);
        assertEquals(energyLimit, energyUsed + result.getEnergyRemaining());

        BigInteger transactionCost = BigInteger.valueOf(energyUsed * energyPrice);
        assertEquals(senderBalance.subtract(transactionCost), kernel.getBalance(from));
    }

    @Test
    public void testSenderBalanceAfterCallValueSent() {
        AionAddress contract = deployContractAndGetAddress();

        BigInteger senderBalance = kernel.getBalance(from);
        BigInteger value = new BigInteger("235762");

        TransactionResult result = callContract(contract, value);
        long energyUsed = ((AvmTransactionResult) result).getEnergyUsed();
        assertTrue(energyUsed > 0);
        assertEquals(energyLimit, energyUsed + result.getEnergyRemaining());

        BigInteger transactionCost = BigInteger.valueOf(energyUsed * energyPrice);
        assertEquals(senderBalance.subtract(transactionCost).subtract(value), kernel.getBalance(from));
    }

    @Test
    public void testSenderBalanceAfterValueTransfer() {
        BigInteger senderBalance = kernel.getBalance(from);
        BigInteger value = new BigInteger("2398652");

        AionAddress recipient = createNewAccountWithBalance(BigInteger.ZERO);
        TransactionResult result = transferValue(recipient, value);
        long energyUsed = ((AvmTransactionResult) result).getEnergyUsed();
        assertTrue(energyUsed > 0);
        assertEquals(energyLimitForValueTransfer, energyUsed + result.getEnergyRemaining());

        BigInteger transactionCost = BigInteger.valueOf(energyUsed * energyPrice);
        assertEquals(senderBalance.subtract(transactionCost).subtract(value), kernel.getBalance(from));
    }

    @Test
    public void testMinerBalanceAfterCreate() {
        BigInteger minerBalance = kernel.getBalance(block.getCoinbase());

        TransactionResult result = deployContract(BigInteger.TEN);
        long energyUsed = ((AvmTransactionResult) result).getEnergyUsed();
        assertTrue(energyUsed > 0);
        assertEquals(energyLimit, energyUsed + result.getEnergyRemaining());

        BigInteger transactionCost = BigInteger.valueOf(energyUsed * energyPrice);
        assertEquals(minerBalance.add(transactionCost), kernel.getBalance(block.getCoinbase()));
    }

    @Test
    public void testMinerBalanceAfterCall() {
        AionAddress contract = deployContractAndGetAddress();
        BigInteger minerBalance = kernel.getBalance(block.getCoinbase());

        TransactionResult result = callContract(contract, BigInteger.TEN);
        long energyUsed = ((AvmTransactionResult) result).getEnergyUsed();
        assertTrue(energyUsed > 0);
        assertEquals(energyLimit, energyUsed + result.getEnergyRemaining());

        BigInteger transactionCost = BigInteger.valueOf(energyUsed * energyPrice);
        assertEquals(minerBalance.add(transactionCost), kernel.getBalance(block.getCoinbase()));
    }

    @Test
    public void testMinerBalanceAfterValueTransfer() {
        BigInteger minerBalance = kernel.getBalance(block.getCoinbase());
        BigInteger value = new BigInteger("2345136");

        AionAddress recipient = createNewAccountWithBalance(BigInteger.ZERO);
        TransactionResult result = transferValue(recipient, value);
        long energyUsed = ((AvmTransactionResult) result).getEnergyUsed();
        assertTrue(energyUsed > 0);
        assertEquals(energyLimitForValueTransfer, energyUsed + result.getEnergyRemaining());

        BigInteger transactionCost = BigInteger.valueOf(energyUsed * energyPrice);
        assertEquals(minerBalance.add(transactionCost), kernel.getBalance(block.getCoinbase()));
    }

    @Test
    public void testDestinationBalanceAfterCreateNoValueSent() {
        TransactionResult result = deployContract(BigInteger.ZERO);
        long energyUsed = ((AvmTransactionResult) result).getEnergyUsed();
        assertTrue(energyUsed > 0);
        assertEquals(energyLimit, energyUsed + result.getEnergyRemaining());

        AionAddress destination = new AionAddress(result.getReturnData());
        assertEquals(BigInteger.ZERO, kernel.getBalance(destination));
    }

    @Test
    public void testDestinationBalanceAfterCreateValueSent() {
        BigInteger value = new BigInteger("23874773");

        TransactionResult result = deployContract(value);
        long energyUsed = ((AvmTransactionResult) result).getEnergyUsed();
        assertTrue(energyUsed > 0);
        assertEquals(energyLimit, energyUsed + result.getEnergyRemaining());

        AionAddress destination = new AionAddress(result.getReturnData());
        assertEquals(value, kernel.getBalance(destination));
    }

    @Test
    public void testDestinationBalanceAfterCallNoValueSent() {
        AionAddress contract = deployContractAndGetAddress();

        BigInteger contractBalance = kernel.getBalance(contract);

        TransactionResult result = callContract(contract, BigInteger.ZERO);
        long energyUsed = ((AvmTransactionResult) result).getEnergyUsed();
        assertTrue(energyUsed > 0);
        assertEquals(energyLimit, energyUsed + result.getEnergyRemaining());

        assertEquals(contractBalance, kernel.getBalance(contract));
    }

    @Test
    public void testDestinationBalanceAfterCallValueSent() {
        AionAddress contract = deployContractAndGetAddress();

        BigInteger contractBalance = kernel.getBalance(contract);
        BigInteger value = new BigInteger("23872325");

        TransactionResult result = callContract(contract, value);
        long energyUsed = ((AvmTransactionResult) result).getEnergyUsed();
        assertTrue(energyUsed > 0);
        assertEquals(energyLimit, energyUsed + result.getEnergyRemaining());

        assertEquals(contractBalance.add(value), kernel.getBalance(contract));
    }

    @Test
    public void testDestinationBalanceAfterValueTransfer() {
        BigInteger initialBalance = new BigInteger("897346532");
        BigInteger value = new BigInteger("2398652");

        AionAddress recipient = createNewAccountWithBalance(initialBalance);
        TransactionResult result = transferValue(recipient, value);
        long energyUsed = ((AvmTransactionResult) result).getEnergyUsed();
        assertTrue(energyUsed > 0);
        assertEquals(energyLimitForValueTransfer, energyUsed + result.getEnergyRemaining());

        assertEquals(initialBalance.add(value), kernel.getBalance(recipient));
    }

    private TransactionResult deployContract(BigInteger value) {
        kernel.generateBlock();
        byte[] jar = JarBuilder.buildJarForMainAndClassesAndUserlib(BasicAppTestTarget.class);
        jar = new CodeAndArguments(jar, new byte[0]).encodeToBytes();

        TestingTransaction transaction = TestingTransaction.create(from, kernel.getNonce(from), value, jar, energyLimit, energyPrice);
        return avm.run(TransactionAccountBalanceTest.kernel, new TestingTransaction[] {transaction})[0].get();
    }

    private AionAddress deployContractAndGetAddress() {
        TransactionResult result = deployContract(BigInteger.ZERO);
        assertTrue(result.getResultCode().isSuccess());
        return new AionAddress(result.getReturnData());
    }

    private TransactionResult callContract(AionAddress contract, BigInteger value) {
        kernel.generateBlock();
        byte[] callData = new ABIStreamingEncoder().encodeOneString("allocateObjectArray").toBytes();
        TestingTransaction transaction = TestingTransaction.call(from, contract, kernel.getNonce(from), value, callData, energyLimit, energyPrice);
        return avm.run(TransactionAccountBalanceTest.kernel, new TestingTransaction[] {transaction})[0].get();
    }

    private TransactionResult transferValue(AionAddress recipient, BigInteger value) {
        kernel.generateBlock();
        TestingTransaction transaction = TestingTransaction.call(from, recipient, kernel.getNonce(from), value, new byte[0], BillingRules.BASIC_TRANSACTION_COST, energyPrice);
        return avm.run(TransactionAccountBalanceTest.kernel, new TestingTransaction[] {transaction})[0].get();
    }

    private AionAddress createNewAccountWithBalance(BigInteger balance) {
        AionAddress account = Helpers.randomAddress();
        kernel.createAccount(account);
        kernel.adjustBalance(account, balance);
        return account;
    }

}
