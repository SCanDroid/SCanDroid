package org.scandroid.testing;

/**
 * Hello world!
 *
 */
public class ArrayLengthTest extends SourceSink {
    
    public static Object main(String[] args) {
        ArrayLengthTest a = new ArrayLengthTest();
        
        return a.echo(args.length);
    }
    
    public Object echo(Object x) {
        return x;
    }
}
