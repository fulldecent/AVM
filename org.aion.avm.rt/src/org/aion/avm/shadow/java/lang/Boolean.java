package org.aion.avm.shadow.java.lang;

import org.aion.avm.internal.IDeserializer;
import org.aion.avm.internal.IInstrumentation;
import org.aion.avm.internal.IObject;
import org.aion.avm.internal.IPersistenceToken;
import org.aion.avm.internal.RuntimeAssertionError;
import org.aion.avm.RuntimeMethodFeeSchedule;

public class Boolean extends Object implements Comparable<Boolean> {
    static {
        // Shadow classes MUST be loaded during bootstrap phase.
        IInstrumentation.attachedThreadInstrumentation.get().bootstrapOnly();
    }

    public static final Boolean avm_TRUE = new Boolean(true);

    public static final Boolean avm_FALSE = new Boolean(false);

    public static final Class<Boolean> avm_TYPE = new Class(java.lang.Boolean.TYPE);

    // These are the constructors provided in the JDK but we mark them private since they are deprecated.
    // (in the future, we may change these to not exist - depends on the kind of error we want to give the user).
    private Boolean(boolean b) {
        this.v = b;
    }
    @SuppressWarnings("unused")
    private Boolean(String s) {
        throw RuntimeAssertionError.unimplemented("This is only provided for a consistent error to user code - not to be called");
    }

    public static boolean avm_parseBoolean(String s){
        IInstrumentation.attachedThreadInstrumentation.get().chargeEnergy(RuntimeMethodFeeSchedule.Boolean_avm_parseBoolean);
        return (s != null) && java.lang.Boolean.parseBoolean(s.getUnderlying());
    }

    public boolean avm_booleanValue() {
        IInstrumentation.attachedThreadInstrumentation.get().chargeEnergy(RuntimeMethodFeeSchedule.Boolean_avm_booleanValue);
        return v;
    }

    public static Boolean avm_valueOf(boolean b) {
        IInstrumentation.attachedThreadInstrumentation.get().chargeEnergy(RuntimeMethodFeeSchedule.Boolean_avm_valueOf);
        return b ? avm_TRUE : avm_FALSE;
    }

    public static Boolean avm_valueOf(String s) {
        IInstrumentation.attachedThreadInstrumentation.get().chargeEnergy(RuntimeMethodFeeSchedule.Boolean_avm_valueOf_1);
        return avm_parseBoolean(s) ? avm_TRUE : avm_FALSE;
    }

    public static String avm_toString(boolean b) {
        IInstrumentation.attachedThreadInstrumentation.get().chargeEnergy(RuntimeMethodFeeSchedule.Boolean_avm_toString);
        return b ? (new String("true")) : (new String("false"));
    }

    public String avm_toString() {
        IInstrumentation.attachedThreadInstrumentation.get().chargeEnergy(RuntimeMethodFeeSchedule.Boolean_avm_toString_1);
        return v ? (new String("true")) : (new String("false"));
    }

    @Override
    public int avm_hashCode() {
        IInstrumentation.attachedThreadInstrumentation.get().chargeEnergy(RuntimeMethodFeeSchedule.Boolean_avm_hashCode);
        return Boolean.avm_hashCode(v);
    }

    public static int avm_hashCode(boolean value) {
        IInstrumentation.attachedThreadInstrumentation.get().chargeEnergy(RuntimeMethodFeeSchedule.Boolean_avm_hashCode_1);
        return value ? 1231 : 1237;
    }

    public boolean avm_equals(IObject obj) {
        IInstrumentation.attachedThreadInstrumentation.get().chargeEnergy(RuntimeMethodFeeSchedule.Boolean_avm_equals);
        if (obj instanceof Boolean) {
            return v == ((Boolean)obj).avm_booleanValue();
        }
        return false;
    }

    public int avm_compareTo(Boolean b) {
        IInstrumentation.attachedThreadInstrumentation.get().chargeEnergy(RuntimeMethodFeeSchedule.Boolean_avm_compareTo);
        return avm_compare(this.v, b.v);
    }

    public static int avm_compare(boolean x, boolean y) {
        IInstrumentation.attachedThreadInstrumentation.get().chargeEnergy(RuntimeMethodFeeSchedule.Boolean_avm_compare);
        return (x == y) ? 0 : (x ? 1 : -1);
    }

    public static boolean avm_logicalAnd(boolean a, boolean b) {
        IInstrumentation.attachedThreadInstrumentation.get().chargeEnergy(RuntimeMethodFeeSchedule.Boolean_avm_logicalAnd);
        return a && b;
    }

    public static boolean avm_logicalOr(boolean a, boolean b) {
        IInstrumentation.attachedThreadInstrumentation.get().chargeEnergy(RuntimeMethodFeeSchedule.Boolean_avm_logicalOr);
        return a || b;
    }

    public static boolean avm_logicalXor(boolean a, boolean b) {
        IInstrumentation.attachedThreadInstrumentation.get().chargeEnergy(RuntimeMethodFeeSchedule.Boolean_avm_logicalXor);
        return a ^ b;
    }

    //=======================================================
    // Methods below are used by runtime and test code only!
    //========================================================

    public Boolean(IDeserializer deserializer, IPersistenceToken persistenceToken) {
        super(deserializer, persistenceToken);
        lazyLoad();
    }

    private boolean v;

    public boolean getUnderlying() {
        return this.v;
    }

    //========================================================
    // Methods below are excluded from shadowing
    //========================================================

    //public static boolean avm_getBoolean(java.lang.String name){}

}
