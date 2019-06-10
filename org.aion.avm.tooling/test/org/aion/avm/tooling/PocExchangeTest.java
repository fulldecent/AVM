package org.aion.avm.tooling;

import org.aion.aion_types.AionAddress;
import org.aion.avm.core.util.ABIUtil;
import avm.Address;
import org.aion.avm.core.AvmConfiguration;
import org.aion.avm.core.AvmImpl;
import org.aion.avm.core.CommonAvmFactory;
import org.aion.avm.core.dappreading.JarBuilder;
import org.aion.avm.tooling.testExchange.*;
import org.aion.avm.core.util.CodeAndArguments;
import org.aion.avm.core.util.Helpers;
import org.aion.kernel.*;
import org.aion.vm.api.interfaces.KernelInterface;
import org.aion.vm.api.interfaces.TransactionResult;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;


public class PocExchangeTest {
    private KernelInterface kernel;
    private AvmImpl avm;
    private byte[] testERC20Jar;
    private byte[] testExchangeJar;

    @Before
    public void setup() {
        TestingBlock block = new TestingBlock(new byte[32], 1, Helpers.randomAddress(), System.currentTimeMillis(), new byte[0]);
        this.kernel = new TestingKernel(block);
        this.avm = CommonAvmFactory.buildAvmInstanceForConfiguration(new StandardCapabilities(), new AvmConfiguration());
        
        testERC20Jar = JarBuilder.buildJarForMainAndClassesAndUserlib(CoinController.class, ERC20Token.class);
        testExchangeJar = JarBuilder.buildJarForMainAndClassesAndUserlib(ExchangeController.class, Exchange.class, ExchangeTransaction.class, ERC20Token.class);
    }

    @After
    public void tearDown() {
        this.avm.shutdown();
    }

    private long energyLimit = 6_000_0000;

    private AionAddress pepeMinter = Helpers.randomAddress();
    private AionAddress memeMinter = Helpers.randomAddress();
    private AionAddress exchangeOwner = Helpers.randomAddress();
    private AionAddress usr1 = Helpers.randomAddress();
    private AionAddress usr2 = Helpers.randomAddress();
    private AionAddress usr3 = Helpers.randomAddress();


    class CoinContract{
        private AionAddress addr;
        private AionAddress minter;

        CoinContract(AionAddress contractAddr, AionAddress minter, byte[] jar, byte[] arguments){
            kernel.adjustBalance(minter, BigInteger.valueOf(1_000_000_000L));
            kernel.adjustBalance(pepeMinter, BigInteger.valueOf(1_000_000_000L));
            kernel.adjustBalance(memeMinter, BigInteger.valueOf(1_000_000_000L));
            kernel.adjustBalance(exchangeOwner, BigInteger.valueOf(1_000_000_000L));
            kernel.adjustBalance(usr1, BigInteger.valueOf(1_000_000_000L));
            kernel.adjustBalance(usr2, BigInteger.valueOf(1_000_000_000L));
            kernel.adjustBalance(usr3, BigInteger.valueOf(1_000_000_000L));

            this.addr = contractAddr;
            this.minter = minter;
            this.addr = initCoin(jar, arguments);
        }

        private AionAddress initCoin(byte[] jar, byte[] arguments){
            TestingTransaction createTransaction = TestingTransaction.create(minter, kernel.getNonce(minter), BigInteger.ZERO, new CodeAndArguments(jar, arguments).encodeToBytes(), energyLimit, 1L);
            TransactionResult createResult = avm.run(PocExchangeTest.this.kernel, new TestingTransaction[] {createTransaction})[0].get();
            Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, createResult.getResultCode());
            return new AionAddress(createResult.getReturnData());
        }

        public TransactionResult callTotalSupply() {
            byte[] args = ABIUtil.encodeMethodArguments("totalSupply");
            return call(minter, args);
        }

        private TransactionResult callBalanceOf(AionAddress toQuery) {
            byte[] args = ABIUtil.encodeMethodArguments("balanceOf", new Address(toQuery.toByteArray()));
            return call(minter, args);
        }

        private TransactionResult callMint(AionAddress receiver, long amount) {
            byte[] args = ABIUtil.encodeMethodArguments("mint", new Address(receiver.toByteArray()), amount);
            return call(minter, args);
        }

        private TransactionResult callTransfer(AionAddress sender, AionAddress receiver, long amount) {
            byte[] args = ABIUtil.encodeMethodArguments("transfer", new Address(receiver.toByteArray()), amount);
            return call(sender, args);
        }

        private TransactionResult callAllowance(AionAddress owner, AionAddress spender) {
            byte[] args = ABIUtil.encodeMethodArguments("allowance", new Address(owner.toByteArray()), new Address(spender.toByteArray()));
            return call(minter, args);
        }

        private TransactionResult callApprove(AionAddress owner, AionAddress spender, long amount) {
            byte[] args = ABIUtil.encodeMethodArguments("approve", new Address(spender.toByteArray()), amount);
            return call(owner, args);
        }

        private TransactionResult callTransferFrom(AionAddress executor, AionAddress from, AionAddress to, long amount) {
            byte[] args = ABIUtil.encodeMethodArguments("transferFrom", new Address(from.toByteArray()), new Address(to.toByteArray()), amount);
            return call(executor, args);
        }

        private TransactionResult call(AionAddress sender, byte[] args) {
            TestingTransaction callTransaction = TestingTransaction.call(sender, addr, kernel.getNonce(sender), BigInteger.ZERO, args, energyLimit, 1l);
            TransactionResult callResult = avm.run(PocExchangeTest.this.kernel, new TestingTransaction[] {callTransaction})[0].get();
            Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, callResult.getResultCode());
            return callResult;
        }
    }

    class ExchangeContract{
        private AionAddress addr;
        private AionAddress owner;

        ExchangeContract(AionAddress contractAddr, AionAddress owner, byte[] jar){
            this.addr = contractAddr;
            this.owner = owner;
            this.addr = initExchange(jar, null);
        }

        private AionAddress initExchange(byte[] jar, byte[] arguments){
            TestingTransaction createTransaction = TestingTransaction.create(owner, kernel.getNonce(owner), BigInteger.ZERO, new CodeAndArguments(jar, arguments).encodeToBytes(), energyLimit, 1L);
            TransactionResult createResult = avm.run(PocExchangeTest.this.kernel, new TestingTransaction[] {createTransaction})[0].get();
            Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, createResult.getResultCode());
            return new AionAddress(createResult.getReturnData());
        }

        public TransactionResult callListCoin(String name, AionAddress coinAddr) {
            byte[] args = ABIUtil.encodeMethodArguments("listCoin", name.toCharArray(), new Address(coinAddr.toByteArray()));
            return call(owner,args);
        }

        public TransactionResult callRequestTransfer(String name, AionAddress from,  AionAddress to, long amount) {
            byte[] args = ABIUtil.encodeMethodArguments("requestTransfer", name.toCharArray(), new Address(to.toByteArray()), amount);
            return call(from,args);
        }

        public TransactionResult callProcessExchangeTransaction(AionAddress sender) {
            byte[] args = ABIUtil.encodeMethodArguments("processExchangeTransaction");
            return call(sender,args);
        }

        private TransactionResult call(AionAddress sender, byte[] args) {
            TestingTransaction callTransaction = TestingTransaction.call(sender, addr, kernel.getNonce(sender), BigInteger.ZERO, args, energyLimit, 1l);
            TransactionResult callResult = avm.run(PocExchangeTest.this.kernel, new TestingTransaction[] {callTransaction})[0].get();
            Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, callResult.getResultCode());
            return callResult;
        }
    }

    @Test
    public void testERC20() {
        TransactionResult res;
        //System.out.println(">> Deploy \"PEPE\" token contract...");
        byte[] arguments = ABIUtil.encodeDeploymentArguments("Pepe", "PEPE", 8);
        CoinContract pepe = new CoinContract(null, pepeMinter, testERC20Jar, arguments);
        //System.out.println(Helpers.bytesToHexString(pepe.addr));

        res = pepe.callTotalSupply();
        //System.out.println(Helpers.bytesToHexString(res.getReturnData()));
        Assert.assertEquals(0L, ABIUtil.decodeOneObject(res.getReturnData()));
        //System.out.println(">> total supply: " + ABIUtil.decodeOneObject(res.getReturnData()));

        res = pepe.callBalanceOf(usr1);
        Assert.assertEquals(0L, ABIUtil.decodeOneObject(res.getReturnData()));
        //System.out.println(">> balance of User1: " + ABIUtil.decodeOneObject(res.getReturnData()));

        res = pepe.callBalanceOf(usr2);
        Assert.assertEquals(0L, ABIUtil.decodeOneObject(res.getReturnData()));
        //System.out.println(">> balance of User2: " + ABIUtil.decodeOneObject(res.getReturnData()));

        res = pepe.callMint(usr1, 5000L);
        Assert.assertEquals(true, ABIUtil.decodeOneObject(res.getReturnData()));
        //System.out.println(">> Mint to deliver 5000 tokens to User1: " + ABIUtil.decodeOneObject(res.getReturnData()));

        res = pepe.callBalanceOf(usr1);
        Assert.assertEquals(5000L, ABIUtil.decodeOneObject(res.getReturnData()));
        //System.out.println(">> balance of User1: " + ABIUtil.decodeOneObject(res.getReturnData()));

        res = pepe.callMint(usr2, 10000L);
        Assert.assertEquals(true, ABIUtil.decodeOneObject(res.getReturnData()));
        //System.out.println(">> Mint to deliver 10000 tokens to User2: " + ABIUtil.decodeOneObject(res.getReturnData()));

        res = pepe.callBalanceOf(usr2);
        Assert.assertEquals(10000L, ABIUtil.decodeOneObject(res.getReturnData()));
        //System.out.println(">> balance of User2: " + ABIUtil.decodeOneObject(res.getReturnData()));

        res = pepe.callTransfer(usr1, usr2, 2000L);
        Assert.assertEquals(true, ABIUtil.decodeOneObject(res.getReturnData()));
        //System.out.println(">> User1 to transfer 2000 tokens to User2: " + ABIUtil.decodeOneObject(res.getReturnData()));

        res = pepe.callBalanceOf(usr1);
        Assert.assertEquals(3000L, ABIUtil.decodeOneObject(res.getReturnData()));
        //System.out.println(">> balance of User1: " + ABIUtil.decodeOneObject(res.getReturnData()));

        res = pepe.callBalanceOf(usr2);
        Assert.assertEquals(12000L, ABIUtil.decodeOneObject(res.getReturnData()));
        //System.out.println(">> balance of User2: " + ABIUtil.decodeOneObject(res.getReturnData()));

        res = pepe.callAllowance(usr1, usr2);
        Assert.assertEquals(0L, ABIUtil.decodeOneObject(res.getReturnData()));
        //System.out.println(">> Allowance User1 grants to User2: " + ABIUtil.decodeOneObject(res.getReturnData()));

        res = pepe.callApprove(usr1, usr3, 1000L);
        Assert.assertEquals(true, ABIUtil.decodeOneObject(res.getReturnData()));
        //System.out.println(">> User1 grants User3 the allowance of 1000 tokens: " + ABIUtil.decodeOneObject(res.getReturnData()));

        res = pepe.callAllowance(usr1, usr3);
        Assert.assertEquals(1000L, ABIUtil.decodeOneObject(res.getReturnData()));
        //System.out.println(">> Allowance User1 grants to User3: " + ABIUtil.decodeOneObject(res.getReturnData()));

        res = pepe.callTransferFrom(usr3, usr1, usr2, 500L);
        Assert.assertEquals(true, ABIUtil.decodeOneObject(res.getReturnData()));
        //System.out.println(">> User3 to transfer 500 tokens to User2, from the allowance granted by User1: " + ABIUtil.decodeOneObject(res.getReturnData()));

        res = pepe.callAllowance(usr1, usr3);
        Assert.assertEquals(500L, ABIUtil.decodeOneObject(res.getReturnData()));
        //System.out.println(">> Allowance User1 grants to User3: " + ABIUtil.decodeOneObject(res.getReturnData()));

        res = pepe.callBalanceOf(usr1);
        Assert.assertEquals(2500L, ABIUtil.decodeOneObject(res.getReturnData()));
        //System.out.println(">> balance of User1: " + ABIUtil.decodeOneObject(res.getReturnData()));

        res = pepe.callBalanceOf(usr2);
        Assert.assertEquals(12500L, ABIUtil.decodeOneObject(res.getReturnData()));
        //System.out.println(">> balance of User2: " + ABIUtil.decodeOneObject(res.getReturnData()));
    }

    @Test
    public void testExchange() {
        //System.out.println(">> Deploy \"PEPE\" token contract...");
        byte[] arguments = ABIUtil.encodeDeploymentArguments("Pepe", "PEPE", 8);
        CoinContract pepe = new CoinContract(null, pepeMinter, testERC20Jar, arguments);

        //System.out.println(">> Deploy \"MEME\" token contract...");
        arguments = ABIUtil.encodeDeploymentArguments("Meme", "MEME", 8);
        CoinContract meme = new CoinContract(null, memeMinter, testERC20Jar, arguments);

        //System.out.println(">> Deploy the Exchange contract...");
        ExchangeContract ex = new ExchangeContract(null, exchangeOwner, testExchangeJar);

        TransactionResult res;

        res = ex.callListCoin("PEPE", pepe.addr);
        Assert.assertEquals(true, ABIUtil.decodeOneObject(res.getReturnData()));
        //System.out.println(">> List \"PEPE\" token on Exchange: " + ABIUtil.decodeOneObject(res.getReturnData()));

        res = ex.callListCoin("MEME", meme.addr);
        Assert.assertEquals(true, ABIUtil.decodeOneObject(res.getReturnData()));
        //System.out.println(">> List \"MEME\" token on Exchange: " + ABIUtil.decodeOneObject(res.getReturnData()));

        res = pepe.callMint(usr1, 5000L);
        //System.out.println(">> Mint to deliver 5000 tokens to User1: " + ABIUtil.decodeOneObject(res.getReturnData()));
        res = pepe.callMint(usr2, 5000L);
        //System.out.println(">> Mint to deliver 5000 tokens to User2: " + ABIUtil.decodeOneObject(res.getReturnData()));

        res = pepe.callApprove(usr1, ex.addr, 2000L);
        Assert.assertEquals(true, ABIUtil.decodeOneObject(res.getReturnData()));
        //System.out.println(">> User1 grants to the Exchange the allowance of 2000 tokens: " + ABIUtil.decodeOneObject(res.getReturnData()));

        res = ex.callRequestTransfer("PEPE", usr1, usr2, 1000L);
        Assert.assertEquals(true, ABIUtil.decodeOneObject(res.getReturnData()));
        //System.out.println(">> Exchange to request transfer 1000 tokens from User1 to User2, from the allowance granted by User1: " + ABIUtil.decodeOneObject(res.getReturnData()));

        //res = pepe.callAllowance(usr1, ex.addr);
        //Assert.assertEquals(2000L, ABIUtil.decodeOneObject(res.getReturnData()));
        //System.out.println(">> User1 grants to the Exchange the allowance of 2000 tokens: " + ABIUtil.decodeOneObject(res.getReturnData()));

        res = ex.callProcessExchangeTransaction(exchangeOwner);
        Assert.assertEquals(true, ABIUtil.decodeOneObject(res.getReturnData()));
        //System.out.println(">> Exchange to process the transactions: " + ABIUtil.decodeOneObject(res.getReturnData()));

        res = pepe.callBalanceOf(usr1);
        Assert.assertEquals(4000L, ABIUtil.decodeOneObject(res.getReturnData()));
        //System.out.println(">> balance of User1: " + ABIUtil.decodeOneObject(res.getReturnData()));

        res = pepe.callBalanceOf(usr2);
        Assert.assertEquals(6000L, ABIUtil.decodeOneObject(res.getReturnData()));
        //System.out.println(">> balance of User2: " + ABIUtil.decodeOneObject(res.getReturnData()));

        res = pepe.callAllowance(usr1, ex.addr);
        Assert.assertEquals(1000L, ABIUtil.decodeOneObject(res.getReturnData()));
        //System.out.println(">> Allowance User1 grants to Exchange: " + ABIUtil.decodeOneObject(res.getReturnData()));
    }
}
