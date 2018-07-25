package org.aion.avm.core;

import org.aion.avm.api.ABIDecoder;
import org.aion.avm.api.ABIEncoder;
import org.aion.avm.api.Address;
import org.aion.avm.api.InvalidTxDataException;
import org.aion.avm.core.util.Helpers;
import org.aion.kernel.*;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class POCTestExchange {

    private static AvmImpl avm;

    private byte[] testERC20Jar;

    @Before
    public void setup() {
        avm = new AvmImpl();
        testERC20Jar = Helpers.readFileToBytes("../examples/build/testExchangeJar/com.example.testERC20.jar");
    }

    private Block block = new Block(1, Helpers.randomBytes(Address.LENGTH), System.currentTimeMillis(), new byte[0]);
    private long energyLimit = 5_000_000;

    private byte[] pepeCoinAddr = Helpers.randomBytes(Address.LENGTH);
    private byte[] memeCoinAddr = Helpers.randomBytes(Address.LENGTH);
    private byte[] pepeMinter = Helpers.randomBytes(Address.LENGTH);
    private byte[] memeMinter = Helpers.randomBytes(Address.LENGTH);
    private byte[] owner = Helpers.randomBytes(Address.LENGTH);
    private byte[] usr1 = Helpers.randomBytes(Address.LENGTH);
    private byte[] usr2 = Helpers.randomBytes(Address.LENGTH);
    private byte[] usr3 = Helpers.randomBytes(Address.LENGTH);

    class CoinContract{
        private byte[] addr;
        private byte[] minter;

        CoinContract(byte[] contractAddr, byte[] minter, byte[] jar, byte[] arguments){
            this.addr = contractAddr;
            this.minter = minter;
            this.addr = initCoin(jar, arguments);
        }

        private byte[] initCoin(byte[] jar, byte[] arguments){
            Transaction createTransaction = new Transaction(Transaction.Type.CREATE, minter, addr, 0, Helpers.encodeCodeAndData(jar, arguments), energyLimit);
            TransactionContext createContext = new TransactionContextImpl(createTransaction, block);
            TransactionResult createResult = avm.run(createContext);
            Assert.assertEquals(TransactionResult.Code.SUCCESS, createResult.getStatusCode());
            return createResult.getReturnData();
        }

        public TransactionResult callTotalSupply() throws InvalidTxDataException {
            byte[] args = ABIEncoder.encodeMethodArguments("totalSupply");

            Transaction callTransaction = new Transaction(Transaction.Type.CALL, minter, addr, 0, args, energyLimit);
            TransactionContext callContext = new TransactionContextImpl(callTransaction, block);
            TransactionResult callResult = avm.run(callContext);
            Assert.assertEquals(TransactionResult.Code.SUCCESS, callResult.getStatusCode());
            return callResult;
        }

        private TransactionResult callBalanceOf(byte[] toQuery) throws InvalidTxDataException {
            byte[] args = ABIEncoder.encodeMethodArguments("balanceOf", new Address(toQuery));

            Transaction callTransaction = new Transaction(Transaction.Type.CALL, minter, addr, 0, args, energyLimit);
            TransactionContext callContext = new TransactionContextImpl(callTransaction, block);
            TransactionResult callResult = avm.run(callContext);
            Assert.assertEquals(TransactionResult.Code.SUCCESS, callResult.getStatusCode());
            return callResult;
        }

        private TransactionResult callOpenAccount(byte[] toOpen) throws InvalidTxDataException {
            byte[] args = ABIEncoder.encodeMethodArguments("openAccount", new Address(toOpen));

            Transaction callTransaction = new Transaction(Transaction.Type.CALL, minter, addr, 0, args, energyLimit);
            TransactionContext callContext = new TransactionContextImpl(callTransaction, block);
            TransactionResult callResult = avm.run(callContext);
            Assert.assertEquals(TransactionResult.Code.SUCCESS, callResult.getStatusCode());
            return callResult;
        }

        private TransactionResult callMint(byte[] receiver, long amount) throws InvalidTxDataException {
            byte[] args = ABIEncoder.encodeMethodArguments("mint", new Address(receiver), amount);

            Transaction callTransaction = new Transaction(Transaction.Type.CALL, minter, addr, 0, args, energyLimit);
            TransactionContext callContext = new TransactionContextImpl(callTransaction, block);
            TransactionResult callResult = avm.run(callContext);
            Assert.assertEquals(TransactionResult.Code.SUCCESS, callResult.getStatusCode());
            return callResult;
        }

        private TransactionResult callTransfer(byte[] sender, byte[] receiver, long amount) throws InvalidTxDataException {
            byte[] args = ABIEncoder.encodeMethodArguments("transfer", new Address(receiver), amount);

            Transaction callTransaction = new Transaction(Transaction.Type.CALL, sender, addr, 0, args, energyLimit);
            TransactionContext callContext = new TransactionContextImpl(callTransaction, block);
            TransactionResult callResult = avm.run(callContext);
            Assert.assertEquals(TransactionResult.Code.SUCCESS, callResult.getStatusCode());
            return callResult;
        }

        private TransactionResult callAllowance(byte[] owner, byte[] spender) throws InvalidTxDataException {
            byte[] args = ABIEncoder.encodeMethodArguments("allowance", new Address(owner), new Address(spender));

            Transaction callTransaction = new Transaction(Transaction.Type.CALL, minter, addr, 0, args, energyLimit);
            TransactionContext callContext = new TransactionContextImpl(callTransaction, block);
            TransactionResult callResult = avm.run(callContext);
            Assert.assertEquals(TransactionResult.Code.SUCCESS, callResult.getStatusCode());
            return callResult;
        }

        private TransactionResult callApprove(byte[] owner, byte[] spender, long amount) throws InvalidTxDataException {
            byte[] args = ABIEncoder.encodeMethodArguments("approve", new Address(spender), amount);

            Transaction callTransaction = new Transaction(Transaction.Type.CALL, owner, addr, 0, args, energyLimit);
            TransactionContext callContext = new TransactionContextImpl(callTransaction, block);
            TransactionResult callResult = avm.run(callContext);
            Assert.assertEquals(TransactionResult.Code.SUCCESS, callResult.getStatusCode());
            return callResult;
        }

        private TransactionResult callTransferFrom(byte[] executor, byte[] from, byte[] to, long amount) throws InvalidTxDataException {
            byte[] args = ABIEncoder.encodeMethodArguments("transferFrom", new Address(from), new Address(to), amount);

            Transaction callTransaction = new Transaction(Transaction.Type.CALL, executor, addr, 0, args, energyLimit);
            TransactionContext callContext = new TransactionContextImpl(callTransaction, block);
            TransactionResult callResult = avm.run(callContext);
            Assert.assertEquals(TransactionResult.Code.SUCCESS, callResult.getStatusCode());
            return callResult;
        }
    }

    @Test
    public void testERC20() throws InvalidTxDataException{

        TransactionResult res;
        byte[] arguments = ABIEncoder.encodeMethodArguments("", "Pepe".toCharArray(), "PEPE".toCharArray(), 8);
        CoinContract pepe = new CoinContract(pepeCoinAddr, pepeMinter, testERC20Jar, arguments);

        res = pepe.callTotalSupply();
        Assert.assertEquals(0L, ABIDecoder.decodeOneObject(res.getReturnData()));

        res = pepe.callBalanceOf(usr1);
        Assert.assertEquals(0L, ABIDecoder.decodeOneObject(res.getReturnData()));
        res = pepe.callBalanceOf(usr1);
        Assert.assertEquals(0L, ABIDecoder.decodeOneObject(res.getReturnData()));

        res = pepe.callMint(usr1, 5000L);
        Assert.assertEquals(true, ABIDecoder.decodeOneObject(res.getReturnData()));
        res = pepe.callBalanceOf(usr1);
        Assert.assertEquals(5000L, ABIDecoder.decodeOneObject(res.getReturnData()));
        res = pepe.callMint(usr2, 10000L);
        Assert.assertEquals(true, ABIDecoder.decodeOneObject(res.getReturnData()));
        res = pepe.callBalanceOf(usr2);
        Assert.assertEquals(10000L, ABIDecoder.decodeOneObject(res.getReturnData()));

        res = pepe.callTransfer(usr1, usr2, 2000L);
        Assert.assertEquals(true, ABIDecoder.decodeOneObject(res.getReturnData()));
        res = pepe.callBalanceOf(usr1);
        Assert.assertEquals(3000L, ABIDecoder.decodeOneObject(res.getReturnData()));
        res = pepe.callBalanceOf(usr2);
        Assert.assertEquals(12000L, ABIDecoder.decodeOneObject(res.getReturnData()));

        res = pepe.callAllowance(usr1, usr2);
        Assert.assertEquals(0L, ABIDecoder.decodeOneObject(res.getReturnData()));
        res = pepe.callApprove(usr1, usr3, 1000L);
        Assert.assertEquals(true, ABIDecoder.decodeOneObject(res.getReturnData()));
        res = pepe.callAllowance(usr1, usr3);
        Assert.assertEquals(1000L, ABIDecoder.decodeOneObject(res.getReturnData()));
        res = pepe.callTransferFrom(usr3, usr1, usr2, 500L);
        Assert.assertEquals(true, ABIDecoder.decodeOneObject(res.getReturnData()));
        res = pepe.callAllowance(usr1, usr3);
        Assert.assertEquals(500L, ABIDecoder.decodeOneObject(res.getReturnData()));
        res = pepe.callBalanceOf(usr1);
        Assert.assertEquals(2500L, ABIDecoder.decodeOneObject(res.getReturnData()));
        res = pepe.callBalanceOf(usr2);
        Assert.assertEquals(12500L, ABIDecoder.decodeOneObject(res.getReturnData()));
    }


    @Test
    public void testExchange() throws InvalidTxDataException{
        byte[] arguments = ABIEncoder.encodeMethodArguments("", "Pepe".toCharArray(), "PEPE".toCharArray(), 8);
        CoinContract pepe = new CoinContract(pepeCoinAddr, pepeMinter, testERC20Jar, arguments);

        arguments = ABIEncoder.encodeMethodArguments("", "Meme".toCharArray(), "MEME".toCharArray(), 8);
        CoinContract meme = new CoinContract(memeCoinAddr, memeMinter, testERC20Jar, arguments);
    }
}