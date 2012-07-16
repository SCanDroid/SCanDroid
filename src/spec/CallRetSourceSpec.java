/*
 *
 * Copyright (c) 2010-2012,
 *
 *  Galois, Inc. (Aaron Tomb <atomb@galois.com>)
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

package spec;

import java.util.Map;
import java.util.Set;

import util.AndroidAppLoader;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.LocalPointerKey;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAInvokeInstruction;

import domain.CodeElement;
import flow.InflowAnalysis;
import flow.types.FlowType;
import flow.types.sources.CallRetArgSourceFlow;
import flow.types.sources.CallRetSourceFlow;

class CallRetSourceSpec extends SourceSpec {
	CallRetSourceSpec(MethodNamePattern name, int[] args) {
		namePattern = name;
		argNums = args;
		myType = SourceType.RETURN_SOURCE;
	}

	@Override
	public<E extends ISSABasicBlock> void addDomainElements(
			Map<BasicBlockInContext<E>, Map<FlowType, Set<CodeElement>>> taintMap,
			IMethod im, BasicBlockInContext<E> block, SSAInvokeInstruction invInst,
			AndroidAppLoader<E> loader, int[] newArgNums) {				
		//if we're not tracking any parameters that's associated with this return value, then just add the class as an input flow
		//covers GetIntentTaintSourcer from prev impl.
		if (newArgNums.length == 0)
			InflowAnalysis.addDomainElements(taintMap, block, new CallRetSourceFlow(invInst, block.getNode()), CodeElement.valueElements(loader.pa, block.getNode(), invInst.getDef(0)));
		//otherwise mark the InstanceKeys associated with this parameter (ie, may be useful in case we need to track which URI is associated with a ContentResolver.query call)
		//covers QueryTaintSourcer from prev impl.
		else {
			for (int j = 0; j<newArgNums.length; j++) 
				for (InstanceKey ik:loader.pa.getPointsToSet(new LocalPointerKey(block.getNode(), invInst.getUse(newArgNums[j]))))
					InflowAnalysis.addDomainElements(taintMap, block, new CallRetArgSourceFlow(ik, invInst, newArgNums[j], block.getNode()), CodeElement.valueElements(loader.pa, block.getNode(), invInst.getDef(0)));
		}
		
	}


}
