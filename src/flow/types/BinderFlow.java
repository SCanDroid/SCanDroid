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

package flow.types;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;

public class BinderFlow implements FlowType {
    public final InstanceKey ik;
    private String inMethod;
    private CGNode inNode;

    public BinderFlow(InstanceKey ik)
    {
        this.ik = ik;
        inMethod = "";
        inNode = null;
    }

    public BinderFlow(InstanceKey ik, String m, CGNode n)
    {
        this.ik = ik;
        inMethod = m;
        inNode = n;
    }

    public void setMethod(String m) {
        inMethod = m;
    }

    public String getMethodName() {
        return inMethod;
    }

    @Override
    public int hashCode()
    {
        return ik.hashCode();
    }

    @Override
    public boolean equals(Object other)
    {
        if(other == null)
            return false;
        if(other instanceof BinderFlow)
            return ((BinderFlow)other).ik.equals(ik);
        return false;
    }

    @Override
    public String toString()
    {
        return "BinderFlow("+ik+" :: "+inMethod+")";
    }
  
    @Override
    public CGNode getRelevantNode()
    {
    	return null;
    }
}
