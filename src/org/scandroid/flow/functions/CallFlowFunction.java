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
package org.scandroid.flow.functions;

import java.util.List;
import java.util.Set;

import org.scandroid.domain.CodeElement;
import org.scandroid.domain.DomainElement;
import org.scandroid.domain.IFDSTaintDomain;

import com.ibm.wala.dataflow.IFDS.IUnaryFlowFunction;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.MutableSparseIntSet;


/**
 * @author creswick
 *
 */
public class CallFlowFunction <E extends ISSABasicBlock> implements IUnaryFlowFunction {

	private final IFDSTaintDomain<E> domain;
	private final List<Set<CodeElement>> actualParams;
	private final List<Set<CodeElement>> formalParams;
	
	public CallFlowFunction(IFDSTaintDomain<E> domain,
		List<Set<CodeElement>> actualParams, List<Set<CodeElement>> formalParams) {
		this.domain = domain;
		this.actualParams = actualParams;
		this.formalParams = formalParams;
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.wala.dataflow.IFDS.IUnaryFlowFunction#getTargets(int)
	 */
	@Override
	public IntSet getTargets(int d1) {
		if (0 == d1) {
			return TaintTransferFunctions.ZERO_SET;
		} 
		DomainElement de = domain.getMappedObject(d1);
        MutableSparseIntSet set = MutableSparseIntSet.makeEmpty();
    	
    	// parameter lists for static methods start at 0,
    	// but for non-static methods they start at 1. (the 0-index param is the
    	// reference object)
    	//int i = invokeStatic ? 0 : 1;
    	
    	// actually, I think the static distinction is moot, since the reference
    	// object is probably treated like a normal param in the SSAInstructions
		int i=0;
		for (Set<CodeElement> actParams : actualParams) {
			for (CodeElement actParam : actParams) {
				if (!actParam.equals(de.codeElement)) {
					continue;
				}
				
				// the query element is a parameter to the function, so
				// it passes through.  We need to find the domain element(s)
				// for the corresponding formal parameter.
				Set<CodeElement> formParams = formalParams.get(i);
				
				for (CodeElement fParam : formParams) {
					int idx = domain.getMappedIndex(
							new DomainElement(fParam, de.taintSource));
					set.add(idx);
				}
			}
			i++;
		}
		return set;
	}
}
