package org.aion.avm.core;

import java.util.HashSet;
import org.aion.avm.NameStyle;
import org.aion.avm.core.types.ClassHierarchy;
import org.aion.avm.core.types.ClassInfo;
import org.aion.avm.core.types.CommonType;
import org.aion.avm.core.types.Forest;
import org.aion.avm.core.types.ClassInformation;
import org.aion.avm.core.types.ClassInformationFactory;
import org.aion.avm.core.types.ClassHierarchyBuilder;
import i.CommonInstrumentation;
import i.IBlockchainRuntime;
import org.aion.avm.core.classloading.AvmClassLoader;
import org.aion.avm.core.util.Helpers;
import i.IInstrumentation;
import i.IRuntimeSetup;
import i.InstrumentationHelpers;
import i.InternedClasses;
import i.RuntimeAssertionError;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public class SimpleAvm {
    private final AvmClassLoader loader;
    private final IRuntimeSetup runtimeSetup;
    private final IInstrumentation instrumentation;

    private ClassHierarchy classHierarchy;
    private ClassRenamer classRenamer;
    private ClassInformationFactory classInfoFactory = new ClassInformationFactory();

    public SimpleAvm(long energyLimit, boolean preserveDebuggability, Class<?>... classes) {
        Map<String, byte[]> preTransformedClassBytecode = new HashMap<>();

        Stream.of(classes).forEach(clazz -> preTransformedClassBytecode.put(clazz.getName(),
                Helpers.loadRequiredResourceAsBytes(clazz.getName().replaceAll("\\.", "/") + ".class")));

        // build class hierarchy
        Forest<String, ClassInfo> classHierarchy = buildOldClassHierarchy(preTransformedClassBytecode);

        this.classHierarchy = buildNewClassHierarchy(preTransformedClassBytecode, preserveDebuggability);

        Set<String> jclExceptions = new HashSet<>();
        for (CommonType type : CommonType.values()) {
            if (type.isShadowException) {
                jclExceptions.add(type.dotName);
            }
        }

        this.classRenamer = new ClassRenamerBuilder(NameStyle.DOT_NAME, preserveDebuggability)
            .loadPreRenameUserDefinedClasses(this.classHierarchy.getPreRenameUserDefinedClassesAndInterfaces())
            .loadPostRenameJclExceptionClasses(jclExceptions)
            .build();

        // transform classes
        Map<String, byte[]> transformedClasses = DAppCreator.transformClasses(preTransformedClassBytecode, classHierarchy, this.classHierarchy, this.classRenamer, preserveDebuggability);
        Map<String, byte[]> finalContractClasses = Helpers.mapIncludingHelperBytecode(transformedClasses, Helpers.loadDefaultHelperBytecode());
        this.loader = NodeEnvironment.singleton.createInvocationClassLoader(finalContractClasses);

        // Create the instrumentation, attach this thread, and push a faked-up frame.
        this.runtimeSetup = Helpers.getSetupForLoader(this.loader);
        this.instrumentation = new CommonInstrumentation();
        InstrumentationHelpers.attachThread(this.instrumentation);
        InstrumentationHelpers.pushNewStackFrame(this.runtimeSetup, this.loader, energyLimit, 1,new InternedClasses());
    }

    private ClassHierarchy buildNewClassHierarchy(Map<String, byte[]> preTransformedClassBytecode, boolean preserveDebuggability) {
        Set<ClassInformation> classInfos = null;

        try {
            classInfos = this.classInfoFactory.fromPreRenameUserDefinedBytecode(preTransformedClassBytecode, preserveDebuggability);
        } catch (ClassNotFoundException e) {
            RuntimeAssertionError.unexpected(e);
        }

        ClassRenamer classRenamer = new ClassRenamerBuilder(NameStyle.DOT_NAME, preserveDebuggability)
            .loadPreRenameUserDefinedClasses(extractClassNames(classInfos))
            .loadPostRenameJclExceptionClasses(fetchPostRenameJclExceptions())
            .build();

        return new ClassHierarchyBuilder()
            .addShadowJcl()
            .addPreRenameUserDefinedClasses(classRenamer, classInfos)
            .addPostRenameJclExceptions()
            .build();
    }

    private Forest<String, ClassInfo> buildOldClassHierarchy(Map<String, byte[]> preTransformedClassBytecode) {
        HierarchyTreeBuilder builder = new HierarchyTreeBuilder();
        preTransformedClassBytecode.entrySet().stream().forEach(e -> {
            try {
                // NOTE: we load the class to figure out the super class instead of by static analysis.
                Class<?> clazz = SimpleAvm.class.getClassLoader().loadClass(e.getKey());
                if (!clazz.isInterface()) {
                    builder.addClass(e.getKey(), clazz.getSuperclass().getName(), false, e.getValue());
                }else{
                    builder.addClass(e.getKey(), Object.class.getName(), true, e.getValue());
                }
            } catch (ClassNotFoundException ex) {
                throw RuntimeAssertionError.unexpected(ex);
            }
        });

        return builder.asMutableForest();
    }

    public ClassHierarchy deepCopyOfClassHierarchy() {
        return this.classHierarchy.deepCopy();
    }

    public ClassRenamer getClassRenamer() {
        return this.classRenamer;
    }

    public void attachBlockchainRuntime(IBlockchainRuntime rt) {
        Helpers.attachBlockchainRuntime(loader, rt);
    }

    public AvmClassLoader getClassLoader() {
        return loader;
    }

    public IRuntimeSetup getRuntimeSetup() {
        return this.runtimeSetup;
    }

    public IInstrumentation getInstrumentation() {
        return this.instrumentation;
    }

    public void shutdown() {
        InstrumentationHelpers.popExistingStackFrame(this.runtimeSetup);
        InstrumentationHelpers.detachThread(this.instrumentation);
    }

    private static Set<String> extractClassNames(Set<ClassInformation> classInformations) {
        Set<String> classNames = new HashSet<>();
        for (ClassInformation classInformation : classInformations) {
            classNames.add(classInformation.dotName);
        }
        return classNames;
    }

    private static Set<String> fetchPostRenameJclExceptions() {
        Set<String> exceptions = new HashSet<>();
        for (CommonType type : CommonType.values()) {
            if (type.isShadowException) {
                exceptions.add(type.dotName);
            }
        }
        return exceptions;
    }
}
