package org.scandroid.testing;

/**
 * Hello world!
 *
 */
public class EchoTest extends SourceSink {
    
    public static Object echoTest(String[] args) {
        EchoTest a = new EchoTest();
        
        return a.echo(args[0]);
    }
    
    public Object echo(Object x) {
        return x;
    }
}
