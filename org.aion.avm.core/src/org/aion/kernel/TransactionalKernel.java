package org.aion.kernel;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.aion.types.AionAddress;
import org.aion.avm.core.types.Pair;
import org.aion.avm.core.util.ByteArrayWrapper;
import org.aion.vm.api.interfaces.KernelInterface;


/**
 * A transactional implementation of the KernelInterface which only writes back to its "parent" on commit.
 * 
 * This uses a relatively extensible pattern for its implementation, building a transaction log rather than its own actual direct implementation.
 * This means that changes to the interface should mostly just translate into a new kind of transaction log entry, in this implementation.
 * Special attention needs to be paid to read-and-write operations (such as adjustBalance()) and anything involving deletes.
 */
public class TransactionalKernel implements KernelInterface {
    private final KernelInterface parent;
    private final CachingKernel writeCache;
    private final List<Consumer<KernelInterface>> writeLog;
    private final Set<ByteArrayWrapper> deletedAccountProjection;
    private final Set<ByteArrayWrapper> cachedAccountBalances;
    private final Set<Pair<AionAddress, ByteArrayWrapper>> deletedStorageKeys;


    private long blockDifficulty;
    private long blockNumber;
    private long blockTimestamp;
    private long blockNrgLimit;
    private AionAddress blockCoinbase;

    public TransactionalKernel(KernelInterface parent) {
        this.parent = parent;
        this.writeCache = new CachingKernel();
        this.writeLog = new ArrayList<>();
        this.deletedAccountProjection = new HashSet<>();
        this.cachedAccountBalances = new HashSet<>();
        this.blockDifficulty = parent.getBlockDifficulty();
        this.blockNumber = parent.getBlockNumber();
        this.blockTimestamp = parent.getBlockTimestamp();
        this.blockNrgLimit = parent.getBlockEnergyLimit();
        this.blockCoinbase = parent.getMinerAddress();
        this.deletedStorageKeys = new HashSet<>();
    }

    @Override
    public TransactionalKernel makeChildKernelInterface() {
        return new TransactionalKernel(this);
    }

    /**
     * Causes the changes enqueued in the receiver to be written back to the parent.
     * After this call, uses of the receiver are undefined.
     */
    @Override
    public void commit() {
        for (Consumer<KernelInterface> mutation : this.writeLog) {
            mutation.accept(this.parent);
        }
    }

    /**
     * Causes the changes enqueued in the receiver to be written back to the target kernel.
     * This method should only be used by AION kernel for database write back.
     */
    @Override
    public void commitTo(KernelInterface target) {
        for (Consumer<KernelInterface> mutation : this.writeLog) {
            mutation.accept(target);
        }
    }

    @Override
    public void createAccount(AionAddress address) {
        Consumer<KernelInterface> write = (kernel) -> {
            kernel.createAccount(address);
        };
        write.accept(writeCache);
        writeLog.add(write);
        this.deletedAccountProjection.remove(new ByteArrayWrapper(address.toByteArray()));
        // Say that we have this cached so we don't go back to any old version in the parent (even though it is unlikely we will create over delete).
        this.cachedAccountBalances.add(new ByteArrayWrapper(address.toByteArray()));
    }

    @Override
    public boolean hasAccountState(AionAddress address) {
        boolean result = false;
        if (!this.deletedAccountProjection.contains(new ByteArrayWrapper(address.toByteArray()))) {
            result = this.writeCache.hasAccountState(address);
            if (!result) {
                result = this.parent.hasAccountState(address);
            }
        }
        return result;
    }

    @Override
    public byte[] getCode(AionAddress address) {
        byte[] result = null;
        if (!this.deletedAccountProjection.contains(new ByteArrayWrapper(address.toByteArray()))) {
            result = this.writeCache.getCode(address);
            if (null == result) {
                result = this.parent.getCode(address);
            }
        }
        return result;
    }

    @Override
    public void putCode(AionAddress address, byte[] code) {
        Consumer<KernelInterface> write = (kernel) -> {
            kernel.putCode(address, code);
        };
        write.accept(writeCache);
        writeLog.add(write);
    }

    @Override
    public byte[] getTransformedCode(AionAddress address) {
        byte[] result = null;
        if (!this.deletedAccountProjection.contains(new ByteArrayWrapper(address.toByteArray()))) {
            result = this.writeCache.getTransformedCode(address);
            if (null == result) {
                result = this.parent.getTransformedCode(address);
            }
        }
        return result;
    }

    @Override
    public void setTransformedCode(AionAddress address, byte[] bytes) {
        Consumer<KernelInterface> write = (kernel) -> {
            kernel.setTransformedCode(address, bytes);
        };
        write.accept(writeCache);
        writeLog.add(write);
    }

    @Override
    public void putObjectGraph(AionAddress address, byte[] bytes) {
        Consumer<KernelInterface> write = (kernel) -> {
            kernel.putObjectGraph(address, bytes);
        };
        write.accept(writeCache);
        writeLog.add(write);
    }

    @Override
    public byte[] getObjectGraph(AionAddress address) {
        byte[] result = this.writeCache.getObjectGraph(address);
        if (null == result) {
            result = this.parent.getObjectGraph(address);
        }
        return result;
    }

    @Override
    public void putStorage(AionAddress address, byte[] key, byte[] value) {
        Consumer<KernelInterface> write = (kernel) -> {
            kernel.putStorage(address, key, value);
        };
        if(deletedStorageKeys.contains(Pair.of(address, new ByteArrayWrapper(key)))){
            deletedStorageKeys.remove(Pair.of(address, new ByteArrayWrapper(key)));
        }
        write.accept(writeCache);
        writeLog.add(write);
    }

    @Override
    public byte[] getStorage(AionAddress address, byte[] key) {
        // We issue these requests from the given address, only, so it is safe for us to decide that we permit reads after deletes.
        // The direct reason why this happens is that DApps which are already running are permitted to continue running but may need to lazyLoad.
        byte[] result = this.writeCache.getStorage(address, key);
        // check if the key has not been deleted
        if (null == result && !deletedStorageKeys.contains(Pair.of(address, new ByteArrayWrapper(key)))) {
            result = this.parent.getStorage(address, key);
        }
        return result;
    }

    @Override
    public void deleteAccount(AionAddress address) {
        Consumer<KernelInterface> write = (kernel) -> {
            kernel.deleteAccount(address);
        };
        write.accept(writeCache);
        writeLog.add(write);
        this.deletedAccountProjection.add(new ByteArrayWrapper(address.toByteArray()));
        this.cachedAccountBalances.remove(new ByteArrayWrapper(address.toByteArray()));
    }

    @Override
    public BigInteger getBalance(AionAddress address) {
        BigInteger result = BigInteger.ZERO;
        if (!this.deletedAccountProjection.contains(new ByteArrayWrapper(address.toByteArray()))) {
            result = this.writeCache.getBalance(address);
            if (result.equals(BigInteger.ZERO)) {
                result = this.parent.getBalance(address);
            }
        }
        return result;
    }

    @Override
    public void adjustBalance(AionAddress address, BigInteger delta) {
        // This is a read-then-write operation so we need to make sure that there is an entry in our cache, first, before we can apply the mutation.
        if (!this.cachedAccountBalances.contains(new ByteArrayWrapper(address.toByteArray()))) {
            // We can only re-cache this if we didn't already delete it.
            // If it was deleted, we need to fake the lazy creation and start it at zero.
            if (!this.deletedAccountProjection.contains(new ByteArrayWrapper(address.toByteArray()))) {
                BigInteger balance = this.parent.getBalance(address);
                this.writeCache.adjustBalance(address, balance);
            } else {
                this.writeCache.adjustBalance(address, BigInteger.ZERO);
            }
            this.cachedAccountBalances.add(new ByteArrayWrapper(address.toByteArray()));
        }
        // If this was previously deleted, fake the lazy re-creation.
        this.deletedAccountProjection.remove(new ByteArrayWrapper(address.toByteArray()));

        Consumer<KernelInterface> write = (kernel) -> {
            kernel.adjustBalance(address, delta);
        };
        write.accept(writeCache);
        writeLog.add(write);
    }

    @Override
    public BigInteger getNonce(AionAddress address) {
        BigInteger result = BigInteger.ZERO;
        if (!this.deletedAccountProjection.contains(new ByteArrayWrapper(address.toByteArray()))) {
            result = this.writeCache.getNonce(address);
            if (result.equals(BigInteger.ZERO)) {
                result = this.parent.getNonce(address);
            }
        }
        return result;
    }

    @Override
    public void incrementNonce(AionAddress address) {
        Consumer<KernelInterface> write = (kernel) -> {
            kernel.incrementNonce(address);
        };
        write.accept(writeCache);
        writeLog.add(write);
    }

    @Override
    public boolean accountNonceEquals(AionAddress address, BigInteger nonce) {
        // Delegate the check to our parent. The actual KernelInterface given to us by the kernel
        // has an opportunity to do some special case logic here when it wishes.
        return this.parent.accountNonceEquals(address, nonce);
    }

    @Override
    public boolean accountBalanceIsAtLeast(AionAddress address, BigInteger amount) {
        // Delegate the check to our parent. The actual KernelInterface given to us by the kernel
        // has an opportunity to do some special case logic here when it wishes.
        return this.parent.accountBalanceIsAtLeast(address, amount);
    }

    @Override
    public boolean isValidEnergyLimitForCreate(long energyLimit) {
        // Delegate the check to our parent. The actual KernelInterface given to us by the kernel
        // has an opportunity to do some special case logic here when it wishes.
        return this.parent.isValidEnergyLimitForCreate(energyLimit);
    }

    @Override
    public boolean isValidEnergyLimitForNonCreate(long energyLimit) {
        // Delegate the check to our parent. The actual KernelInterface given to us by the kernel
        // has an opportunity to do some special case logic here when it wishes.
        return this.parent.isValidEnergyLimitForNonCreate(energyLimit);
    }

    @Override
    public void refundAccount(AionAddress address, BigInteger amount) {
        // This method may have special logic in the kernel. Here it is just adjustBalance.
        adjustBalance(address, amount);
    }

    @Override
    public void deductEnergyCost(AionAddress address, BigInteger cost) {
        // This method may have special logic in the kernel. Here it is just adjustBalance.
        adjustBalance(address, cost);
    }

    @Override
    public void payMiningFee(AionAddress address, BigInteger fee) {
        // This method may have special logic in the kernel. Here it is just adjustBalance.
        adjustBalance(address, fee);
    }

    @Override
    public byte[] getBlockHashByNumber(long blockNumber) {
        throw new AssertionError("No equivalent concept in the Avm.");
    }

    @Override
    public void removeStorage(AionAddress address, byte[] key) {
        Consumer<KernelInterface> write = (kernel) -> {
            kernel.removeStorage(address, key);
        };
        deletedStorageKeys.add(Pair.of(address, new ByteArrayWrapper(key)));
        write.accept(writeCache);
        writeLog.add(write);
    }

    @Override
    public boolean destinationAddressIsSafeForThisVM(AionAddress address) {
        // We need to delegate to our parent kernel to apply whatever logic is defined there.
        // The only exception to this is cases where we already stored code in our cache so see if that is there.
        return (null != this.writeCache.getTransformedCode(address)) || this.parent.destinationAddressIsSafeForThisVM(address);
    }

    @Override
    public long getBlockNumber() {
        return blockNumber;
    }

    @Override
    public long getBlockTimestamp() {
        return blockTimestamp;
    }

    @Override
    public long getBlockEnergyLimit() {
        return blockNrgLimit;
    }

    @Override
    public long getBlockDifficulty() {
        return blockDifficulty;
    }

    @Override
    public AionAddress getMinerAddress() {
        return blockCoinbase;
    }
}
