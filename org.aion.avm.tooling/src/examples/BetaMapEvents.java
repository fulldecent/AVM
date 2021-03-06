package examples;

import avm.Blockchain;
import org.aion.avm.tooling.abi.Callable;
import org.aion.avm.userlib.AionMap;


/**
 * A simple example for the Beta release.
 * Exposes 2 methods:
 * 1) put(String, String)
 * 2) get(String)
 * 
 * These methods operate on the state of a shared static map, producing debug log and event log output based on these actions.
 */
public class BetaMapEvents {
    /**
     * AVM doesn't expose all the java.util containers, provides its own set:
     * -AionMap
     * -AionList
     * -AionSet
     * Since this map is a static, it will be part of the graph saved to persistent storage between transactions.
     */
    private static final AionMap<String, String> map;

    /**
     * AVM only runs the static initializer (<clinit> method) on deployment.
     * Additional data passed to the deployment can be accessed here, too.
     */
    static {
        map = new AionMap<>();
        Blockchain.println("Deployed BetaMapEvents");
    }

    @Callable
    public static void put(String key, String value) {
        String oldValue = map.put(key, value);
        Blockchain.println("PUT(\"" + key + "\", \"" + value + "\") -> " + oldValue);
        Blockchain.log("PUT".getBytes(), key.getBytes(), value.getBytes());
        if (null != oldValue) {
            Blockchain.log("REPLACE".getBytes(), key.getBytes(), oldValue.getBytes());
        }
    }

    @Callable
    public static void get(String key) {
        String value = map.get(key);
        Blockchain.println("GET(\"" + key + "\") -> " + value);
        String reportingValue = (null != value)
                ? value
                : "null";
        Blockchain.log("GET".getBytes(), key.getBytes(), reportingValue.getBytes());
    }
}
