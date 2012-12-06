/*
 *
 * Copyright (c) 2009-2012,
 *
 *  Galois, Inc. (Aaron Tomb <atomb@galois.com>, Rogan Creswick <creswick@galois.com>)
 *  Steve Suh    <suhsteve@gmail.com>
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. The names of the contributors may not be used to endorse or promote
 * products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 *
 */
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
    
    public void shrinkNull() {
    	shrink(null);
    }
    
    public void shrink2() {
    	List<String> ls = new ArrayList<String>();
    	ls.add(null);
    }
    
    public void shrink3(ArrayList<String> out) {
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
