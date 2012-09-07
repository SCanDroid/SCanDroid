/*
 *
 * Copyright (c) 2009-2012,
 *
 *  Steve Suh           <suhsteve@gmail.com>
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

import java.util.Set;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.intset.OrdinalSet;

import domain.CodeElement;

public class ParameterFlow <E extends ISSABasicBlock> implements FlowType<E> {

    public final BasicBlockInContext<E> block;
    public final CGNode node;
    public final int argNum;
    
    public ParameterFlow(BasicBlockInContext<E> block, int argNum)
    {
    	this.block = block;
    	this.argNum = argNum;
    	this.node = null;
    }
    public ParameterFlow(CGNode node, int argNum)
    {
        this.node = node;
        this.argNum = argNum;
        this.block = null;
    }

    @Override
    public int hashCode()
    {
        return block.hashCode()*argNum;
    }

    @Override
    public boolean equals(Object other)
    {
    	return other != null & other instanceof ParameterFlow && 
    			((ParameterFlow<E>)other).block.equals(block) && ((ParameterFlow<E>)other).argNum == argNum;
    }

    @Override
    public String toString()
    {
    	return "ParameterFlow("+argNum+", "+block+")";
    }

    @Override
    public BasicBlockInContext<E> getBlock()
    {
    	return block;
    }
    
}
