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
import com.ibm.wala.types.TypeReference;

public class InputFlow implements FlowType {

    public final TypeReference activityClass;
    private final CGNode inNode;
    final String type;
    final int argNum;
    final String callee;

    public InputFlow(TypeReference activityClass)
    {
        this.activityClass = activityClass;
        inNode = null;
        type = "";
        argNum = 0;
        callee = "";
    }
    
    public InputFlow(TypeReference activityClass, CGNode node, String type, String callee)
    {
        this.activityClass = activityClass;
        this.inNode = node;
        this.type = type;
        argNum = 0;
        this.callee = callee;
    }
    
    public InputFlow(TypeReference activityClass, CGNode node, String type,String callee, int argNum)
    {
        this.activityClass = activityClass;
        this.inNode = node;
        this.type = type;
        this.argNum = argNum;
        this.callee = callee;
    }
    
    @Override
    public int hashCode()
    {
        return activityClass.hashCode();
    }

    @Override
    public boolean equals(Object other)
    {
//        return other != null && other instanceof InputFlow && ((InputFlow)other).activityClass.equals(activityClass);
    	return other != null && other instanceof InputFlow && ((InputFlow)other).inNode.equals(inNode)
    			&& ((InputFlow)other).argNum == argNum;
    }

    @Override
    public String toString()
    {
    	if (argNum == 0)
    		return type+" - InputFlow(Caller:"+inNode.getMethod().getSignature()+" ==> Callee:"+callee+")";
    	else
    		return type+" - InputFlow(Caller:"+inNode.getMethod().getSignature()+" ==> Callee:"+callee+", Parameter:"+argNum+")";
//        return "InputFlow("+activityClass+")";
    }

	@Override
	public CGNode getRelevantNode() {
		return inNode;
	}
	
}
