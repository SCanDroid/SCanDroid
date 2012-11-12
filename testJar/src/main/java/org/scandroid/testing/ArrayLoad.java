package org.scandroid.testing;

public class ArrayLoad {
    public static void flow(String[] args) {
        SourceSink.sink(arrayFlow(args));
    }

    public static String arrayFlow(String[] args) {
        return args[0];
    }
    
    public String copyElement(String[] in) {
    	String[] ret = new String[1];
    	ret[0] = in[0];
    	return ret[0];
    }
}
