package org.scandroid.testing;

/**
 * Hello world!
 *
 */
public class InvokeCallArgTest extends SourceSink {
    
    public static Object main(String[] args) {
        return invokeCallArgSourceSpec(args[0]);
    }
    
    public static String invokeCallArgSourceSpec(String s) {
    	char[] buff = new char[1];
    	load(buff);
        return (s + new String(buff));
    }

	private static void load(char[] buff) {
		buff[0] = '.';
	}
}