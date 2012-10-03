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
    
    public Object getStr() {
        return str;
    }
}
