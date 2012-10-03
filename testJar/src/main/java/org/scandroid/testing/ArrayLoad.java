package org.scandroid.testing;

public class ArrayLoad {
    public static void flow(String[] args) {
        SourceSink.sink(useFlow(args));
    }

    private static String useFlow(String[] args) {
        return args[0];
    }
}
