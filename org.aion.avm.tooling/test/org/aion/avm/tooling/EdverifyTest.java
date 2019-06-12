package org.aion.avm.tooling;

import net.i2p.crypto.eddsa.Utils;
import avm.Address;
import org.aion.avm.core.util.ABIUtil;
import org.aion.kernel.AvmTransactionResult;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.math.BigInteger;

public class EdverifyTest {
    @Rule
    public AvmRule avmRule = new AvmRule(false);

    private static byte[] publicKeyBytes = Utils.hexToBytes("8c11e9a4772bb651660a5a5e412be38d33f26b2de0487115d472a6c8bf60aa19");
    private byte[] testMessage = "test message".getBytes();
    private byte[] messageSignature = Utils.hexToBytes("0367f714504761427cbc4abd5e4af97bbaa88553a7fa0076dc2fefdd200eca61fb73777830ba3e92e4aa1832d41ec00c6e1d3bdd193e779cc2d51cb10d908c0a");

    private long energyLimit = 10_000_000L;
    private long energyPrice = 1L;

    private Address deployer = avmRule.getPreminedAccount();
    private Address dappAddress;

    @Before
    public void setup() {
        byte[] txData = avmRule.getDappBytes(EdverifyTestTargetClass.class, null);
        dappAddress = avmRule.deploy(deployer, BigInteger.ZERO, txData, energyLimit, energyPrice).getDappAddress();
    }

    @Test
    public void testVerifyCorrectness(){
        byte[] txData = ABIUtil.encodeMethodArguments("callEdverify", testMessage, messageSignature, publicKeyBytes);

        AvmTransactionResult txResult = avmRule.call(deployer, dappAddress, BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();

        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, txResult.getResultCode());
        Assert.assertTrue((Boolean) ABIUtil.decodeOneObject(txResult.getReturnData()));
    }

    @Test
    public void testVerifyFailIncorrectSignature(){
        byte[] incorrectSignature = messageSignature;
        incorrectSignature[0] = (byte) (int)(incorrectSignature[0] + 1);

        byte[] txData = ABIUtil.encodeMethodArguments("callEdverify", testMessage, incorrectSignature, publicKeyBytes);

        AvmTransactionResult txResult = avmRule.call(deployer, dappAddress, BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();

        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, txResult.getResultCode());
        Assert.assertFalse((Boolean) ABIUtil.decodeOneObject(txResult.getReturnData()));
    }

    @Test
    public void testVerifyFailIncorrectPublicKey(){
        byte[] incorrectPublicKey = publicKeyBytes;
        incorrectPublicKey[1] = (byte) (int)(incorrectPublicKey[1] + 1);

        byte[] txData = ABIUtil.encodeMethodArguments("callEdverify", testMessage, messageSignature, incorrectPublicKey);

        AvmTransactionResult txResult = avmRule.call(deployer, dappAddress, BigInteger.ZERO, txData, energyLimit, energyPrice).getTransactionResult();

        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, txResult.getResultCode());
        Assert.assertFalse((Boolean) ABIUtil.decodeOneObject(txResult.getReturnData()));
    }
}
