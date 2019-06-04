package org.aion.avm.core.collection;

import java.math.BigInteger;
import org.aion.types.AionAddress;
import org.aion.avm.core.AvmConfiguration;
import org.aion.avm.core.AvmImpl;
import org.aion.avm.core.CommonAvmFactory;
import org.aion.avm.core.blockchainruntime.EmptyCapabilities;
import org.aion.avm.core.dappreading.JarBuilder;
import org.aion.avm.core.util.CodeAndArguments;
import org.aion.avm.core.util.ABIUtil;
import org.aion.avm.core.util.Helpers;
import org.aion.kernel.*;
import org.aion.vm.api.interfaces.KernelInterface;
import org.junit.Assert;
import org.junit.Test;


public class AionCollectionPerfTest {

    private AionAddress from = TestingKernel.PREMINED_ADDRESS;
    private TestingBlock block = new TestingBlock(new byte[32], 1, Helpers.randomAddress(), System.currentTimeMillis(), new byte[0]);
    private long energyLimit = 100_000_000L;
    private long energyPrice = 1;

    private byte[] buildListPerfJar() {
        return JarBuilder.buildJarForMainAndClassesAndUserlib(AionListPerfContract.class);
    }

    private byte[] buildSetPerfJar() {
        return JarBuilder.buildJarForMainAndClassesAndUserlib(AionSetPerfContract.class);
    }

    private byte[] buildMapPerfJar() {
        return JarBuilder.buildJarForMainAndClassesAndUserlib(AionMapPerfContract.class);
    }

    private AvmTransactionResult deploy(KernelInterface kernel, AvmImpl avm, byte[] testJar){


        byte[] testWalletArguments = new byte[0];
        TestingTransaction createTransaction = TestingTransaction.create(from, kernel.getNonce(from), BigInteger.ZERO, new CodeAndArguments(testJar, testWalletArguments).encodeToBytes(), energyLimit, energyPrice);
        AvmTransactionResult createResult = avm.run(kernel, new TestingTransaction[] {createTransaction})[0].get();

        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, createResult.getResultCode());

        return createResult;
    }


    private AvmTransactionResult call(KernelInterface kernel, AvmImpl avm, AionAddress contract, AionAddress sender, byte[] args) {
        TestingTransaction callTransaction = TestingTransaction.call(sender, contract, kernel.getNonce(sender), BigInteger.ZERO, args, energyLimit, 1l);
        AvmTransactionResult callResult = avm.run(kernel, new TestingTransaction[] {callTransaction})[0].get();
        Assert.assertEquals(AvmTransactionResult.Code.SUCCESS, callResult.getResultCode());
        return callResult;
    }

    @Test
    public void testList() {
        System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
        System.out.println(">> Energy measurement for AionList");
        byte[] args;
        KernelInterface kernel = new TestingKernel(block);
        AvmImpl avm = CommonAvmFactory.buildAvmInstanceForConfiguration(new EmptyCapabilities(), new AvmConfiguration());

        AvmTransactionResult deployRes = (AvmTransactionResult) deploy(kernel, avm, buildListPerfJar());
        AionAddress contract = new AionAddress(deployRes.getReturnData());

        args = ABIUtil.encodeMethodArguments("callInit");
        AvmTransactionResult initResult = (AvmTransactionResult) call(kernel, avm, contract, from, args);
        args = ABIUtil.encodeMethodArguments("callAppend");
        AvmTransactionResult appendResult = (AvmTransactionResult) call(kernel, avm, contract, from, args);
        System.out.println(">> Append        : " + appendResult.getEnergyUsed() / AionListPerfContract.SIZE);

        args = ABIUtil.encodeMethodArguments("callInit");
        call(kernel, avm, contract, from, args);
        args = ABIUtil.encodeMethodArguments("callInsertHead");
        AvmTransactionResult insertHeadResult = (AvmTransactionResult) call(kernel, avm, contract, from, args);
        System.out.println(">> Insert Head   : " + insertHeadResult.getEnergyUsed() / AionListPerfContract.SIZE);

        args = ABIUtil.encodeMethodArguments("callInit");
        call(kernel, avm, contract, from, args);

        args = ABIUtil.encodeMethodArguments("callInsertMiddle");
        AvmTransactionResult insertMiddleResult = (AvmTransactionResult) call(kernel, avm, contract, from, args);
        System.out.println(">> Insert Middle : " + insertMiddleResult.getEnergyUsed() / AionListPerfContract.SIZE);

        System.out.println("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");

        avm.shutdown();
    }

    @Test
    public void testSet() {
        System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
        System.out.println(">> Energy measurement for AionSet");
        byte[] args;
        KernelInterface kernel = new TestingKernel(block);
        AvmImpl avm = CommonAvmFactory.buildAvmInstanceForConfiguration(new EmptyCapabilities(), new AvmConfiguration());

        AvmTransactionResult deployRes = (AvmTransactionResult) deploy(kernel, avm, buildSetPerfJar());
        AionAddress contract = new AionAddress(deployRes.getReturnData());

        args = ABIUtil.encodeMethodArguments("callInit");
        AvmTransactionResult initResult = (AvmTransactionResult) call(kernel, avm, contract, from, args);

        args = ABIUtil.encodeMethodArguments("callAdd");
        AvmTransactionResult addResult = (AvmTransactionResult) call(kernel, avm, contract, from, args);
        System.out.println(">> Add           : " + addResult.getEnergyUsed() / AionSetPerfContract.SIZE);

        args = ABIUtil.encodeMethodArguments("callContains");
        AvmTransactionResult containsResult = (AvmTransactionResult) call(kernel, avm, contract, from, args);
        System.out.println(">> Contains      : " + containsResult.getEnergyUsed() / AionSetPerfContract.SIZE);

        args = ABIUtil.encodeMethodArguments("callRemove");
        AvmTransactionResult removeReult = (AvmTransactionResult) call(kernel, avm, contract, from, args);
        System.out.println(">> Remove        : " + removeReult.getEnergyUsed() / AionSetPerfContract.SIZE);

        System.out.println("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");

        System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
        System.out.println(">> Energy measurement for AionPlainSet");

        args = ABIUtil.encodeMethodArguments("callInitB");
        initResult = (AvmTransactionResult) call(kernel, avm, contract, from, args);

        args = ABIUtil.encodeMethodArguments("callAddB");
        addResult = (AvmTransactionResult) call(kernel, avm, contract, from, args);
        System.out.println(">> Add           : " + addResult.getEnergyUsed() / AionSetPerfContract.SIZE);

        args = ABIUtil.encodeMethodArguments("callContainsB");
        containsResult = (AvmTransactionResult) call(kernel, avm, contract, from, args);
        System.out.println(">> Contains      : " + containsResult.getEnergyUsed() / AionSetPerfContract.SIZE);

        args = ABIUtil.encodeMethodArguments("callRemoveB");
        removeReult = (AvmTransactionResult) call(kernel, avm, contract, from, args);
        System.out.println(">> Remove        : " + removeReult.getEnergyUsed() / AionSetPerfContract.SIZE);

        System.out.println("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
        avm.shutdown();
    }

    @Test
    public void testMap() {

        System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
        System.out.println(">> Energy measurement for AionMap");
        byte[] args;
        KernelInterface kernel = new TestingKernel(block);
        AvmImpl avm = CommonAvmFactory.buildAvmInstanceForConfiguration(new EmptyCapabilities(), new AvmConfiguration());

        AvmTransactionResult deployRes = (AvmTransactionResult) deploy(kernel, avm, buildMapPerfJar());
        AionAddress contract = new AionAddress(deployRes.getReturnData());

        args = ABIUtil.encodeMethodArguments("callInit");
        AvmTransactionResult initResult = (AvmTransactionResult) call(kernel, avm, contract, from, args);

        args = ABIUtil.encodeMethodArguments("callPut");
        AvmTransactionResult putResult = (AvmTransactionResult) call(kernel, avm, contract, from, args);
        System.out.println(">> Put           : " + putResult.getEnergyUsed() / AionMapPerfContract.SIZE);

        args = ABIUtil.encodeMethodArguments("callGet");
        AvmTransactionResult getResult = (AvmTransactionResult) call(kernel, avm, contract, from, args);
        System.out.println(">> Get           : " + getResult.getEnergyUsed() / AionMapPerfContract.SIZE);

        args = ABIUtil.encodeMethodArguments("callRemove");
        AvmTransactionResult removeResult = (AvmTransactionResult) call(kernel, avm, contract, from, args);
        System.out.println(">> Remove        : " + removeResult.getEnergyUsed() / AionMapPerfContract.SIZE);

        System.out.println("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");

        System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
        System.out.println(">> Energy measurement for AionPlainMap");

        args = ABIUtil.encodeMethodArguments("callInitB");
        initResult = (AvmTransactionResult) call(kernel, avm, contract, from, args);

        args = ABIUtil.encodeMethodArguments("callPutB");
        putResult = (AvmTransactionResult) call(kernel, avm, contract, from, args);
        System.out.println(">> Put           : " + putResult.getEnergyUsed() / AionMapPerfContract.SIZE);

        args = ABIUtil.encodeMethodArguments("callGetB");
        getResult = (AvmTransactionResult) call(kernel, avm, contract, from, args);
        System.out.println(">> Get           : " + getResult.getEnergyUsed() / AionMapPerfContract.SIZE);

        args = ABIUtil.encodeMethodArguments("callRemoveB");
        removeResult = (AvmTransactionResult) call(kernel, avm, contract, from, args);
        System.out.println(">> Remove        : " + removeResult.getEnergyUsed() / AionMapPerfContract.SIZE);

        System.out.println("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
        avm.shutdown();
    }

}
