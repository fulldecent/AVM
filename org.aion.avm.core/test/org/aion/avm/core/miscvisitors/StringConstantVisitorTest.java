package org.aion.avm.core.miscvisitors;

import org.aion.avm.core.ClassToolchain;
import org.aion.avm.core.SimpleRuntime;
import org.aion.avm.core.classgeneration.CommonGenerators;
import org.aion.avm.core.classloading.AvmClassLoader;
import org.aion.avm.core.classloading.AvmSharedClassLoader;
import org.aion.avm.core.util.Helpers;
import org.aion.avm.internal.Helper;
import org.aion.avm.api.Address;
import org.aion.avm.internal.PackageConstants;
import org.junit.*;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;


public class StringConstantVisitorTest {
    private static AvmSharedClassLoader sharedClassLoader;

    private static String runtimeClassName;

    @BeforeClass
    public static void setupClass() {
        sharedClassLoader = new AvmSharedClassLoader(CommonGenerators.generateExceptionShadowsAndWrappers());
        runtimeClassName = PackageConstants.kInternalSlashPrefix + "Helper";
    }


    private Class<?> clazz;
    private Class<?> clazzNoStatic;

    @Before
    public void setup() throws Exception {
        String targetTestName = StringConstantVisitorTestTarget.class.getName();
        byte[] targetTestBytes = Helpers.loadRequiredResourceAsBytes(targetTestName.replaceAll("\\.", "/") + ".class");
        String targetNoStaticName = StringConstantVisitorTestTargetNoStatic.class.getName();
        byte[] targetNoStaticBytes = Helpers.loadRequiredResourceAsBytes(targetNoStaticName.replaceAll("\\.", "/") + ".class");
        
        Function<byte[], byte[]> transformer = (inputBytes) ->
                new ClassToolchain.Builder(inputBytes, ClassReader.SKIP_DEBUG)
                        .addNextVisitor(new UserClassMappingVisitor(Set.of(targetTestName, targetNoStaticName)))
                        .addNextVisitor(new ConstantVisitor(runtimeClassName))
                        .addWriter(new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS))
                        .build()
                        .runAndGetBytecode();
        Map<String, byte[]> classes = new HashMap<>();
        classes.put(PackageConstants.kUserDotPrefix + targetTestName, transformer.apply(targetTestBytes));
        classes.put(PackageConstants.kUserDotPrefix + targetNoStaticName, transformer.apply(targetNoStaticBytes));
        AvmClassLoader loader = new AvmClassLoader(sharedClassLoader, classes);

        // We don't really need the runtime but we do need the intern map initialized.
        new Helper(loader, 1_000_000L);
        this.clazz = loader.loadUserClassByOriginalName(targetTestName);
        this.clazzNoStatic = loader.loadUserClassByOriginalName(targetNoStaticName);
    }

    @After
    public void clearTestingState() {
        Helper.clearTestingState();
    }

    @Test
    public void testLoadStringConstant() throws Exception {
        Object obj = this.clazz.getConstructor().newInstance();
        
        // Get the constant via the method.
        Method method = this.clazz.getMethod(UserClassMappingVisitor.mapMethodName("returnStaticStringConstant"));
        Object ret = method.invoke(obj);
        Assert.assertEquals(StringConstantVisitorTestTarget.kStringConstant, ret.toString());
        
        // Get the constant directly from the static field.
        Object direct = this.clazz.getField(UserClassMappingVisitor.mapFieldName("kStringConstant")).get(null);
        Assert.assertEquals(StringConstantVisitorTestTarget.kStringConstant, direct.toString());
        
        // They should also be the same instance.
        Assert.assertTrue(ret == direct);
    }

    @Test
    public void testLoadStringConstantNoStatic() throws Exception {
        Object obj = this.clazzNoStatic.getConstructor().newInstance();
        
        // Get the constant via the method.
        Method method = this.clazzNoStatic.getMethod(UserClassMappingVisitor.mapMethodName("returnStaticStringConstant"));
        Object ret = method.invoke(obj);
        Assert.assertEquals(StringConstantVisitorTestTarget.kStringConstant, ret.toString());
        
        // Get the constant directly from the static field.
        Object direct = this.clazzNoStatic.getField(UserClassMappingVisitor.mapFieldName("kStringConstant")).get(null);
        Assert.assertEquals(StringConstantVisitorTestTarget.kStringConstant, direct.toString());
        
        // They should also be the same instance.
        Assert.assertTrue(ret == direct);
    }
}
