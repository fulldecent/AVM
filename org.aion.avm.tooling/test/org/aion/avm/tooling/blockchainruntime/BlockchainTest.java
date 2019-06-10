package org.aion.avm.tooling.blockchainruntime;

import java.math.BigInteger;
import avm.Address;
import org.aion.types.AionAddress;
import org.aion.avm.core.dappreading.JarBuilder;
import org.aion.avm.core.util.CodeAndArguments;
import org.aion.avm.tooling.AvmRule;
import org.aion.avm.tooling.hash.HashUtils;
import org.aion.avm.userlib.AionBuffer;
import org.junit.ClassRule;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;


public class BlockchainTest {

    @ClassRule
    public static AvmRule avmRule = new AvmRule(true);

    private Address premined = avmRule.getPreminedAccount();

    private static long energyLimit = 5_000_000L;
    private static long energyPrice = 5;

    @Test
    public void testBlockchainRuntime() {
        Address dappAddress = installJarAsDApp(avmRule.getDappBytes(BlockchainTestResource.class, new byte[0], AionBuffer.class));
        byte[] txData = "tx_data".getBytes();

        AvmRule.ResultWrapper result = avmRule.call(premined, dappAddress, BigInteger.ONE, txData, energyLimit, energyPrice);
        ByteBuffer returnData = getReturnData(dappAddress, txData);

        byte[] expected = Arrays.copyOfRange(returnData.array(), 0, returnData.position());
        assertArrayEquals(expected, result.getTransactionResult().getReturnData());
    }

    @Test
    public void testIncorrectParameters() {
        byte[] jar = JarBuilder.buildJarForMainAndClasses(BlockchainRuntimeTestFailingResource.class);
        byte[] codeAndArgs =  new CodeAndArguments(jar, new byte[0]).encodeToBytes();
        Address dappAddress = installJarAsDApp(codeAndArgs);
        byte[] txData = "expectFailure".getBytes();

        AvmRule.ResultWrapper result = avmRule.call(premined, dappAddress, BigInteger.ONE, txData, energyLimit, energyPrice);

        assertTrue(result.getReceiptStatus().isSuccess());
        // We expect it to handle all the exceptions and return the data we initially sent in.
        assertArrayEquals(txData, result.getTransactionResult().getReturnData());
    }

    @Test
    public void testAVMContractCodeDistinguish() {
        byte[] jar = avmRule.getDappBytes(BlockchainTestResource.class, new byte[0], AionBuffer.class);
        Address dappAddress = installJarAsDApp(jar);
        AionAddress dappAddressApi = new AionAddress(dappAddress.toByteArray());

        CodeAndArguments decodeFromBytes = CodeAndArguments.decodeFromBytes(jar);
        assertEquals(decodeFromBytes.code.length, avmRule.kernel.getCode(dappAddressApi).length);

        assertNotEquals(avmRule.kernel.getCode(dappAddressApi).length, avmRule.kernel.getTransformedCode(dappAddressApi).length);

        byte[] txData = "tx_data".getBytes();
        AvmRule.ResultWrapper result = avmRule.call(premined, dappAddress, BigInteger.ONE, txData, energyLimit, energyPrice);
        assertTrue(result.getTransactionResult().getResultCode().isSuccess());

        ByteBuffer returnData = getReturnData(dappAddress, txData);
        byte[] expected = Arrays.copyOfRange(returnData.array(), 0, returnData.position());
        assertArrayEquals(expected, result.getTransactionResult().getReturnData());
    }

    private Address installJarAsDApp(byte[] jar) {
        AvmRule.ResultWrapper result = avmRule.deploy(premined, BigInteger.ZERO, jar, energyLimit, energyPrice);
        assertTrue(result.getTransactionResult().getResultCode().isSuccess());
        return result.getDappAddress();
    }

    private ByteBuffer getReturnData(Address dappAddress, byte[] txData) {
        AionAddress dappAddressApi = new AionAddress(dappAddress.toByteArray());

        ByteBuffer buffer = ByteBuffer.allocate(1024);
        buffer.put(dappAddress.toByteArray());
        buffer.put(premined.toByteArray());
        buffer.put(premined.toByteArray());
        buffer.putLong(energyLimit);
        buffer.putLong(energyPrice);
        buffer.putLong(BigInteger.ONE.longValue());
        buffer.put(txData);
        buffer.putLong(avmRule.kernel.getBlockTimestamp());
        buffer.putLong(avmRule.kernel.getBlockNumber());
        buffer.putLong(avmRule.kernel.getBlockEnergyLimit());
        buffer.put(avmRule.kernel.getMinerAddress().toByteArray());
        buffer.put(BigInteger.valueOf(avmRule.kernel.getBlockDifficulty()).toByteArray());
        buffer.put("value".getBytes());
        buffer.putLong(avmRule.kernel.getBalance(new AionAddress(new byte[32])).longValue());
        buffer.putLong(avmRule.kernel.getCode(dappAddressApi).length);
        buffer.put(HashUtils.blake2b("blake2b-message".getBytes()));
        buffer.put(HashUtils.sha256("sha256-message".getBytes()));
        buffer.put(HashUtils.keccak256("keccak256-message".getBytes()));

        return buffer;
    }
}
