package org.scandroid.testing;

/**
 * Hello world!
 *
 */
public class LocalVarFlow extends SourceSink {
    
    public static void flow(String[] args) {
        SourceSink.sink(localParam(args[0]));
    }
    
    public static String localParam(String s) {
    	String local = s;
    	return local;
    }
}
