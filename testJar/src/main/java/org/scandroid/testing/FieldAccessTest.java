package org.scandroid.testing;

/**
 * Hello world!
 *
 */
public class FieldAccessTest extends SourceSink {
    
    public static String str;
    
    public static Object main(String[] args) {
        str = args[0];
        
        FieldAccessTest a = new FieldAccessTest();
        
        return a.getStr();
    }

	private Integer val = new Integer(2048);
    
    public String getStr() {
        return str;
    }
    
    public String getClassField() {
    	return val.toString();
    }
}
