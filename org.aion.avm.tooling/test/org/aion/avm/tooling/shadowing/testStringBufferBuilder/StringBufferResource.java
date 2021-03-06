package org.aion.avm.tooling.shadowing.testStringBufferBuilder;

import avm.Blockchain;
import org.aion.avm.tooling.abi.Callable;

public class StringBufferResource {

    @Callable
    public static void stringBufferInsertNull() {
        StringBuffer s = new StringBuffer();

        s.insert(0, (Object) null);
        Blockchain.require(s.toString().equals("null"));

        s.insert(4, (String) null);
        Blockchain.require(s.toString().equals("nullnull"));

        s.insert(8, (CharSequence) null);
        Blockchain.require(s.toString().equals("nullnullnull"));

        s.insert(12, (CharSequence) null, 0, 1);
        Blockchain.require(s.toString().equals("nullnullnulln"));

        boolean thrown = false;
        try {
            s.insert(0, (char[]) null);
        } catch (NullPointerException e) {
            thrown = true;
        }
        Blockchain.require(thrown);
    }

    @Callable
    public static void stringBufferAppendNull() {
        StringBuffer s = new StringBuffer();

        s.append((Object) null);
        Blockchain.require(s.toString().equals("null"));

        s.append((String) null);
        Blockchain.require(s.toString().equals("nullnull"));

        s.append((StringBuffer) null);
        Blockchain.require(s.toString().equals("nullnullnull"));

        s.append((CharSequence) null);
        Blockchain.require(s.toString().equals("nullnullnullnull"));

        boolean thrown = false;
        try {
            s.append((char[]) null);
        } catch (NullPointerException e) {
            thrown = true;
        }
        Blockchain.require(thrown);
    }

    @Callable
    public static void insertCharacterSequence() {
        StringBuffer sb1 = new StringBuffer();
        for (int i = 0; i < 10; i++) {
            int achar = i + 60;
            char test = (char) (achar);
            sb1.append(test);
        }
        String s = sb1.toString();
        StringBuffer sb2 = new StringBuffer();
        sb2.append((CharSequence) sb1);
        Blockchain.require(sb2.toString().equals(s));
    }

    @Callable
    public static void appendStringBuffer() {
        StringBuffer sb1 = new StringBuffer("StringBuffer1");
        StringBuffer sb2 = new StringBuffer("StringBuffer2");
        StringBuffer sb3 = new StringBuffer("StringBuffer3");
        String s1 = sb1.toString();
        String s2 = sb2.toString();
        String s3 = sb3.toString();

        String concatResult = s1 + s2 + s3;

        StringBuffer sb4 = new StringBuffer();
        sb4.append(sb1);
        sb4.append(sb2);
        sb4.append(sb3);
        Blockchain.require(sb4.toString().equals(concatResult));
    }

    @Callable
    public static void insertMaxValue() {
        StringBuffer sb = new StringBuffer("new StringBuffer");
        try {
            sb.insert(2, new char[25], 5, Integer.MAX_VALUE);
            throw new RuntimeException("Exception expected");
        } catch (StringIndexOutOfBoundsException e) {
            // Expected exception thrown
        }
    }

    @Callable
    public static void indexOf() {
        StringBuffer sb = new StringBuffer("xyyz");
        Blockchain.require(sb.indexOf("y", 0) == 1);
        Blockchain.require(sb.indexOf("y", 1) == 1);
        Blockchain.require(sb.indexOf("y", 2) == 2);
        Blockchain.require(sb.indexOf("index") == -1);
    }

    @Callable
    public static void indexOfNull() {
        try {
            StringBuffer sb1 = new StringBuffer();
            sb1.indexOf(null, 1);
            throw new RuntimeException("Test failed: should have thrown NPE");
        } catch (NullPointerException npe) {
            // Expected exception thrown
        }
    }

    @Callable
    public static void appendDelete() {

        String str = "\uFF21";
        Blockchain.require(new StringBuffer(str).append(new char[]{'A'}).toString().equals("\uFF21A"));
        Blockchain.require(new StringBuffer(str).insert(0, new char[]{}).toString().equals("\uFF21"));
        Blockchain.require(new StringBuffer(str).insert(1, new char[]{'A'}).toString().equals("\uFF21A"));

        Blockchain.require(new StringBuffer(str).delete(0, 1).toString().equals(""));
        Blockchain.require(new StringBuffer(str).delete(0, 0).toString().equals("\uFF21"));
        Blockchain.require(new StringBuffer(str).deleteCharAt(0).toString().equals(""));

        Blockchain.require(new StringBuffer(str).insert(0, "").toString().equals("\uFF21"));
        Blockchain.require(new StringBuffer(str).insert(0, "A").toString().equals("A\uFF21"));
        Blockchain.require(new StringBuffer(str).insert(1, "A").toString().equals("\uFF21A"));

        Blockchain.require(new StringBuffer(str).lastIndexOf("\uFF21") == 0);
        Blockchain.require(new StringBuffer(str).lastIndexOf("") == 1);

        Blockchain.require(new StringBuffer(str).replace(0, 0, "A").toString().equals("A\uFF21"));
        Blockchain.require(new StringBuffer(str).replace(0, 1, "A").toString().equals("A"));

        StringBuffer sb = new StringBuffer(str);
        sb.setCharAt(0, 'A');
        Blockchain.require(sb.toString().equals("A"));

        Blockchain.require(new StringBuffer(str).substring(0).equals("\uFF21"));
        Blockchain.require(new StringBuffer(str).substring(1).equals(""));
    }

    @Callable
    public static void setLength() {
        StringBuffer active = new StringBuffer();
        active.append("My StringBuffer");
        active.setLength(0);
        Blockchain.require(active.length() == 0);
    }


    @Callable
    public static void stringBufferAddObject() {
        StringBuffer sb = new StringBuffer();
        SampleClass sampleClass = new SampleClass();
        sb.append(sampleClass);
        Blockchain.require(sb.toString().equals(sampleClass.toString()));

        sb.insert(sampleClass.toString().length(), sampleClass);
        Blockchain.require(sb.toString().equals(sampleClass.toString() + sampleClass.toString()));

    }

    @Callable
    public static void stringBufferConstructor() {
        StringBuffer sb = new StringBuffer(100);
        Blockchain.require(sb.length() == 0);

        StringBuffer sb2 = new StringBuffer("MyString");
        Blockchain.require(sb2.toString().equals("MyString"));

        StringBuffer sb3 = new StringBuffer((CharSequence) "MyString");
        Blockchain.require(sb3.toString().equals("MyString"));
    }

    public static class SampleClass{}
}
