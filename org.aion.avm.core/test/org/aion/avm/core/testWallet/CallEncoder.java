package org.aion.avm.core.testWallet;

import org.aion.avm.api.Address;
import org.aion.avm.api.InvalidTxDataException;


/**
 * Creates the invocation encoding which Wallet.decode can crack, on the other side.
 * This is on its own, outside of CallProxy, since it can be used for both the transformed and direct variants of the call.
 */
public class CallEncoder {
    public static byte[] init(Address extra1, Address extra2, int requiredVotes, long dailyLimit) throws InvalidTxDataException {
        byte[] onto = new byte[1 + Integer.BYTES + Address.LENGTH + Address.LENGTH + Integer.BYTES + Long.BYTES];
        Abi.Encoder encoder = Abi.buildEncoder(onto);
        // We are encoding the Addresses as a 2-element array, so describe it that way to the encoder.
        encoder
            .encodeByte(Abi.kWallet_init)
            .encodeInt(2)
            .encodeAddress(extra1)
            .encodeAddress(extra2)
            .encodeInt(requiredVotes)
            .encodeLong(dailyLimit);
        return onto;
    }
    public static byte[] payable(Address from, long value) throws InvalidTxDataException {
        byte[] onto = new byte[1 + Address.LENGTH + Long.BYTES];
        Abi.Encoder encoder = Abi.buildEncoder(onto);
        encoder
            .encodeByte(Abi.kWallet_payable)
            .encodeAddress(from)
            .encodeLong(value);
        return onto;
    }
    public static byte[] addOwner(Address owner) throws InvalidTxDataException {
        byte[] onto = new byte[1 + Address.LENGTH];
        Abi.Encoder encoder = Abi.buildEncoder(onto);
        encoder
            .encodeByte(Abi.kWallet_addOwner)
            .encodeAddress(owner);
        return onto;
    }
    public static byte[] execute(Address to, long value, byte[] data) throws InvalidTxDataException {
        byte[] onto = new byte[1 + Address.LENGTH + Long.BYTES + data.length];
        Abi.Encoder encoder = Abi.buildEncoder(onto);
        encoder
            .encodeByte(Abi.kWallet_execute)
            .encodeAddress(to)
            .encodeLong(value)
            .encodeRemainder(data);
        return onto;
    }
    public static byte[] confirm(byte[] data) throws InvalidTxDataException {
        byte[] onto = new byte[1 + data.length];
        Abi.Encoder encoder = Abi.buildEncoder(onto);
        encoder
            .encodeByte(Abi.kWallet_confirm)
            .encodeRemainder(data);
        return onto;
    }
    public static byte[] changeRequirement(int newRequired) throws InvalidTxDataException {
        byte[] onto = new byte[1 + Integer.BYTES];
        Abi.Encoder encoder = Abi.buildEncoder(onto);
        encoder
            .encodeByte(Abi.kWallet_changeRequirement)
            .encodeInt(newRequired);
        return onto;
    }
    public static byte[] getOwner(int ownerIndex) throws InvalidTxDataException {
        byte[] onto = new byte[1 + Integer.BYTES];
        Abi.Encoder encoder = Abi.buildEncoder(onto);
        encoder
            .encodeByte(Abi.kWallet_getOwner)
            .encodeInt(ownerIndex);
        return onto;
    }
    public static byte[] changeOwner(Address from, Address to) throws InvalidTxDataException {
        byte[] onto = new byte[1 + Address.LENGTH + Address.LENGTH];
        Abi.Encoder encoder = Abi.buildEncoder(onto);
        encoder
            .encodeByte(Abi.kWallet_changeOwner)
            .encodeAddress(from)
            .encodeAddress(to);
        return onto;
    }
    public static byte[] removeOwner(Address owner) throws InvalidTxDataException {
        byte[] onto = new byte[1 + Address.LENGTH];
        Abi.Encoder encoder = Abi.buildEncoder(onto);
        encoder
            .encodeByte(Abi.kWallet_removeOwner)
            .encodeAddress(owner);
        return onto;
    }
    public static byte[] revoke(byte[] transactionBytes) throws InvalidTxDataException {
        byte[] onto = new byte[1 + transactionBytes.length];
        Abi.Encoder encoder = Abi.buildEncoder(onto);
        encoder
            .encodeByte(Abi.kWallet_revoke);
        encoder.encodeRemainder(transactionBytes);
        return onto;
    }
}
