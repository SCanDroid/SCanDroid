package org.scandroid.testing;

public class ArrayLoad {
    public static void flow(String[] args) {
        SourceSink.sink(useFlow(args));
    }

    private static String useFlow(String[] args) {
        return args[0];
    }
    
    public Object copyElement(Object[] in) {
    	Object[] ret = new Object[1];
    	ret[0] = in[0];
    	return ret[0];
    }
}
