package org.scandroid.testing;

import java.util.ArrayList;
import java.util.List;

/**
 * Hello world!
 */
public class LLTestIter extends SourceSink {
     
    public static Object main(String[] args) {
        LLTestIter a = new LLTestIter();
        
        LList list = new LList();
        list.next = new LList();
        list.next.element = args[0];
        
        return a.last(list);
    }
    
    public int echo2(int y) {
        return echo(y);
    }
    
    public int echo(int x) {
        return x;
    }
    
    public void argToArg(List<String> in, List<String> out) {
        for (String s : in) {
            out.add(s);
        }
    }
    
    public void shrink(List<String> out) {
    	out.add(null);
    }
    
    /**
     * Recurse on a simple linked list.
     *
     * @param list
     * @return The last element, or null if the list is empty.
     */
    public String last(LList list) {
        LList l = list;
        while(l.next != null) l = l.next;
        return l.element;
    }
}
