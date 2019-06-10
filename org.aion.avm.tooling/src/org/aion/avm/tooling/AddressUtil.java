package org.aion.avm.tooling;

import java.math.BigInteger;
import java.nio.ByteBuffer;

import org.aion.aion_types.AionAddress;
import org.aion.avm.tooling.hash.HashUtils;
import org.aion.kernel.TestingKernel;
import org.aion.vm.api.interfaces.TransactionInterface;


/**
 * This is a temporary helper class until the contract address generation logic can be moved
 * into the calling kernel (since it depends on the blockchain design, not the VM).
 */
public class AddressUtil {
    public static AionAddress generateContractAddress(TransactionInterface tx) {
        AionAddress sender = tx.getSenderAddress();
        long nonce = new BigInteger(tx.getNonce()).longValue();
        ByteBuffer buffer = ByteBuffer.allocate(32 + 8).put(sender.toByteArray()).putLong(nonce);
        byte[] hash = HashUtils.sha256(buffer.array());
        // NOTE: This implemenation assumes are being used on the testing kernel.
        hash[0] = TestingKernel.AVM_CONTRACT_PREFIX;
        return new AionAddress(hash);
    }
}
