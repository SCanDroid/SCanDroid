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
    
    public static Object echoTest(String arg) {
        EchoTest a = new EchoTest();
        
        return a.echo(arg);
    }
    
    public Object echo(Object x) {
        return x;
    }
    
    public static Object static_echo(Object x) {
        return x;
    }
    
    public String echo2(String x) {
        return x;
    }
    
    public static String static_echo2(String x) {
        return x;
    }
}
