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


/**
 * 
 * A return flow represents either a flow from a method that was just invoked
 * if this points to an invoke instruction (in which case this is a source) or
 * a method return, if this points to a return instruction (in which case it is
 * a sink).
 *
 */
public class ReturnFlow implements FlowType {

    public final TypeReference activityClass;
    private final CGNode inNode;
    final String type;
    final int argNum;
    final String callee;

    public ReturnFlow(TypeReference activityClass)
    {
        this.activityClass = activityClass;
        inNode = null;
        argNum = 0;
        type = "";
        callee = "";
    }
    
    public ReturnFlow(TypeReference activityClass, CGNode node, String type, String callee)
    {
        this.activityClass = activityClass;
        inNode = node;
        this.type = type;
        argNum = 0;
        this.callee = callee;
    }
    
    public ReturnFlow(TypeReference activityClass, CGNode node, String type, String callee, int argNum)
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
//        return other != null && other instanceof ReturnFlow && ((ReturnFlow)other).activityClass.equals(activityClass);
    	return other != null && other instanceof ReturnFlow && ((ReturnFlow)other).inNode.equals(inNode)
    			&& ((ReturnFlow)other).argNum == argNum;
    }

    @Override
    public String toString()
    {
    	if (argNum == 0)
    		return type+" - ReturnFlow(Caller:"+inNode.getMethod().getSignature()+" ==> Callee:"+callee+")";
    	else
    		return type+" - ReturnFlow(Caller:"+inNode.getMethod().getSignature()+" ==> Callee:"+callee+", Parameter:"+argNum+")";

//        return "ReturnFlow("+activityClass+")";
    	
    }

	@Override
	public CGNode getRelevantNode() {
		return inNode;
	}

}
