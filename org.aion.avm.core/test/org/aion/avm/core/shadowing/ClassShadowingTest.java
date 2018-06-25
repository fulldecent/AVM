package org.aion.avm.core.shadowing;

import org.aion.avm.core.ClassToolchain;
import org.aion.avm.core.classgeneration.CommonGenerators;
import org.aion.avm.core.classloading.AvmClassLoader;
import org.aion.avm.core.classloading.AvmSharedClassLoader;
import org.aion.avm.core.miscvisitors.ConstantVisitor;
import org.aion.avm.core.miscvisitors.UserClassMappingVisitor;
import org.aion.avm.core.util.Helpers;
import org.aion.avm.internal.Helper;
import org.aion.avm.internal.PackageConstants;
import org.junit.After;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;


public class ClassShadowingTest {
    private static AvmSharedClassLoader sharedClassLoader;
    private static String runtimeClassName;

    @BeforeClass
    public static void setupClass() {
        sharedClassLoader = new AvmSharedClassLoader(CommonGenerators.generateExceptionShadowsAndWrappers());
        runtimeClassName = Helpers.fulllyQualifiedNameToInternalName(Testing.class.getName());
    }

    @After
    public void clearTestingState() {
        Helper.clearTestingState();
        Testing.countWrappedStrings = 0;
        Testing.countWrappedClasses = 0;
    }

    @Test
    public void testReplaceJavaLang() throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        String className = TestResource.class.getName();
        byte[] bytecode = Helpers.loadRequiredResourceAsBytes(className.replaceAll("\\.", "/") + ".class");

        Function<byte[], byte[]> transformer = (inputBytes) ->
                new ClassToolchain.Builder(inputBytes, ClassReader.SKIP_DEBUG)
                        .addNextVisitor(new UserClassMappingVisitor(Set.of(className)))
                        .addNextVisitor(new ConstantVisitor(runtimeClassName))
                        .addNextVisitor(new ClassShadowing(runtimeClassName))
                        .addWriter(new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS))
                        .build()
                        .runAndGetBytecode();
        Map<String, byte[]> classes = new HashMap<>();
        classes.put(PackageConstants.kUserDotPrefix + className, transformer.apply(bytecode));
        AvmClassLoader loader = new AvmClassLoader(sharedClassLoader, classes);

        // We don't really need the runtime but we do need the intern map initialized.
        new Helper(loader, 1_000_000L);
        Class<?> clazz = loader.loadUserClassByOriginalName(className);
        Object obj = clazz.getConstructor().newInstance();

        Method method = clazz.getMethod(UserClassMappingVisitor.mapMethodName("abs"), int.class);
        Object ret = method.invoke(obj, -10);
        Assert.assertEquals(10, ret);

        // Verify that we haven't created any wrapped instances, yet.
        Assert.assertEquals(0, Testing.countWrappedStrings);
        Assert.assertEquals(0, Testing.countWrappedClasses);

        // We can rely on our test-facing toString methods to look into what we got back.
        Object wrappedClass = clazz.getMethod(UserClassMappingVisitor.mapMethodName("returnClass")).invoke(obj);
        Assert.assertEquals("class org.aion.avm.shadow.java.lang.String", wrappedClass.toString());
        Object wrappedString = clazz.getMethod(UserClassMappingVisitor.mapMethodName("returnString")).invoke(obj);
        Assert.assertEquals("hello", wrappedString.toString());

        // Verify that we see wrapped instances.
        Assert.assertEquals(1, Testing.countWrappedStrings);
        Assert.assertEquals(1, Testing.countWrappedClasses);
    }

    @Test
    public void testField() throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        String className = TestResource2.class.getName();
        String mappedClassName = PackageConstants.kUserDotPrefix + className;
        byte[] bytecode = Helpers.loadRequiredResourceAsBytes(className.replaceAll("\\.", "/") + ".class");

        Function<byte[], byte[]> transformer = (inputBytes) ->
                new ClassToolchain.Builder(inputBytes, 0) /* DO NOT SKIP ANYTHING */
                        .addNextVisitor(new UserClassMappingVisitor(Collections.singleton(className)))
                        .addNextVisitor(new ConstantVisitor(runtimeClassName))
                        .addNextVisitor(new ClassShadowing(runtimeClassName))
                        .addWriter(new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS))
                        .build()
                        .runAndGetBytecode();

        Map<String, byte[]> classes = new HashMap<>();
        classes.put(mappedClassName, transformer.apply(bytecode));

        Set<String> loadedClasses = new HashSet<>();
        AvmClassLoader loader = new AvmClassLoader(sharedClassLoader, classes) {
            @Override
            public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                loadedClasses.add(name);
                return super.loadClass(name, resolve);
            }
        };

        // We don't really need the runtime but we do need the intern map initialized.
        new Helper(loader, 1_000_000L);

        Class<?> clazz = loader.loadClass(mappedClassName);
        Object obj = clazz.getConstructor().newInstance();

        //Method method = clazz.getMethod(UserClassMappingVisitor.mapMethodName("getStatic");
        //Object ret = method.invoke(obj);
        //Assert.assertTrue(loadedClasses.contains(PackageConstants.kShadowJavaLangDotPrefix + "Byte"));

        Method method2 = clazz.getMethod(UserClassMappingVisitor.mapMethodName("localVariable"));
        Object ret2 = method2.invoke(obj);
        Assert.assertEquals(Integer.valueOf(3), ret2);
    }

    @Test
    public void testInterfaceHandling() throws Exception {
        String className = TestResourceInterface.class.getName();
        byte[] bytecode = Helpers.loadRequiredResourceAsBytes(className.replaceAll("\\.", "/") + ".class");

        String innerClassName = className + "$1";
        byte[] innerBytecode = Helpers.loadRequiredResourceAsBytes(innerClassName.replaceAll("\\.", "/") + ".class");

        Function<byte[], byte[]> transformer = (inputBytes) ->
                new ClassToolchain.Builder(inputBytes, ClassReader.SKIP_DEBUG)
                        .addNextVisitor(new UserClassMappingVisitor(Set.of(className, innerClassName)))
                        .addNextVisitor(new ConstantVisitor(runtimeClassName))
                        .addNextVisitor(new ClassShadowing(runtimeClassName))
                        .addWriter(new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS))
                        .build()
                        .runAndGetBytecode();
        Map<String, byte[]> classes = new HashMap<>();
        byte[] transformed = transformer.apply(bytecode);
        classes.put(PackageConstants.kUserDotPrefix + className, transformed);
        classes.put(PackageConstants.kUserDotPrefix + innerClassName, transformer.apply(innerBytecode));

        AvmClassLoader loader = new AvmClassLoader(sharedClassLoader, classes);

        // We don't really need the runtime but we do need the intern map initialized.
        new Helper(loader, 1_000_000L);
        Class<?> clazz = loader.loadUserClassByOriginalName(className);

        Method method = clazz.getMethod(UserClassMappingVisitor.mapMethodName("getStringForNull"));
        Object ret = method.invoke(null);
        // Note that we can't yet override methods in our contracts so the toString returns false, from Object.
        Assert.assertEquals(null, ret);
    }

    public static class Testing {
        public static int countWrappedClasses;
        public static int countWrappedStrings;

        public static <T> org.aion.avm.shadow.java.lang.Class<T> wrapAsClass(Class<T> input) {
            countWrappedClasses += 1;
            return Helper.wrapAsClass(input);
        }

        public static org.aion.avm.shadow.java.lang.String wrapAsString(String input) {
            countWrappedStrings += 1;
            return Helper.wrapAsString(input);
        }
    }
}