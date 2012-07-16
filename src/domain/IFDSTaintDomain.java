/*
 *
 * Copyright (c) 2009-2012,
 *
 *  Adam Fuchs          <afuchs@cs.umd.edu>
 *  Avik Chaudhuri      <avik@cs.umd.edu>
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

package domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.ibm.wala.dataflow.IFDS.PathEdge;
import com.ibm.wala.dataflow.IFDS.TabulationDomain;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ssa.ISSABasicBlock;

public class IFDSTaintDomain <E extends ISSABasicBlock>
  implements TabulationDomain<DomainElement, BasicBlockInContext<E>> {
    private Map<DomainElement, Integer> table = new HashMap<DomainElement, Integer>();
    private ArrayList<DomainElement> objects = new ArrayList<DomainElement>();

    private Map<CodeElement, Set<DomainElement>> elementIndex = new HashMap<CodeElement, Set<DomainElement>>();

    Set<DomainElement> emptySet = new HashSet<DomainElement>();
    public Set<DomainElement> getPossibleElements(CodeElement codeElement)
    {
        Set<DomainElement> elts = elementIndex.get(codeElement);
        if(elts != null)
            return elts;
        return emptySet;
    }

    private void index(DomainElement e)
    {
        Set<DomainElement> elements = elementIndex.get(e.codeElement);
        if(elements == null)
        {
            elements = new HashSet<DomainElement>();
            elementIndex.put(e.codeElement, elements);
        }
        elements.add(e);
    }

    public int add(DomainElement o) {
        Integer i = table.get(o);
        if(i == null)
        {
            objects.add(o);
            i = table.size() + 1;
            table.put(o, i);
            //System.out.println("Adding domain element "+i+": "+o);
        }
        index(o);

        return i;
    }


    public synchronized int getMappedIndex(DomainElement o)
    {
        Integer i = table.get(o);
        if(i == null)
            return add(o);
        return i;
    }

    public boolean hasPriorityOver(
            PathEdge<BasicBlockInContext<E>> p1,
            PathEdge<BasicBlockInContext<E>> p2) {
        return false;
    }

    public DomainElement getMappedObject(int n) {
        if(n > 0 && n <= objects.size())
            return objects.get(n - 1);
        return null;
    }

    public int getMaximumIndex() {
        return objects.size();
    }

    public int getSize() {
        return objects.size()+1;
    }

    public boolean hasMappedIndex(DomainElement o) {
        return table.keySet().contains(o);
    }

    public Iterator<DomainElement> iterator() {
        return table.keySet().iterator();
    }
}
