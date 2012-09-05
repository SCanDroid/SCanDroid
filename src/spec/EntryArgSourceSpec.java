/*
 *
 * Copyright (c) 2009-2012,
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

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.dataflow.IFDS.ISupergraph;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.LocalPointerKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAInvokeInstruction;

import domain.CodeElement;
import domain.InstanceKeyElement;
import domain.LocalElement;
import flow.InflowAnalysis;
import flow.types.BinderFlow;
import flow.types.FlowType;
import flow.types.InputFlow;
import flow.types.ReturnFlow;

/**
 * Entry arg source specs reperesent sources that are arguments to methods 
 * that are entry points.
 * 
 * For example, the command line arguments to a {@code main(String[] args)}
 * are entry arg sources.
 * 
 */
public class EntryArgSourceSpec extends SourceSpec {

	final String name = "EntryArgSource";
	
	EntryArgSourceSpec(MethodNamePattern name, int[] args) {
        namePattern = name;
        argNums = args;
        myType = SourceType.INPUT_SOURCE;
    }
    
	EntryArgSourceSpec(MethodNamePattern name, int[] args, SourceType type) {
        namePattern = name;
        argNums = args;
        myType = type;
    }
    
	@Override
	public<E extends ISSABasicBlock> void addDomainElements(
			Map<BasicBlockInContext<E>, Map<FlowType, Set<CodeElement>>> taintMap,
			IMethod im, BasicBlockInContext<E> block_notused, SSAInvokeInstruction invInst_notused,
			int[] newArgNums, 
			ISupergraph<BasicBlockInContext<E>, CGNode> graph, PointerAnalysis pa, CallGraph cg) {

		CGNode node = cg.getNode(im, Everywhere.EVERYWHERE);

		switch (myType) {
		case INPUT_SOURCE:
			for (BasicBlockInContext<E> block : graph.getEntriesForProcedure(node) )
			{
//				FlowType flowType = new InputFlow(im.getReference().getDeclaringClass(), node, name);
				for (int i = 0; i < newArgNums.length; i++) {
					FlowType flowType = new InputFlow(im.getReference().getDeclaringClass(), node, name, im.getSignature(), newArgNums[i]);
					InflowAnalysis.addDomainElements(taintMap, block, flowType, CodeElement.valueElements(pa, node, node.getIR().getParameter(newArgNums[i])));
				}
			}
			break;
		case RETURN_SOURCE:
			for (BasicBlockInContext<E> block : graph.getEntriesForProcedure(node) )
			{
//				FlowType flowType = new ReturnFlow(im.getReference().getDeclaringClass(), node, name);
				for (int i = 0; i < newArgNums.length; i++) {
					FlowType flowType = new ReturnFlow(im.getReference().getDeclaringClass(), node, name, im.getSignature(), newArgNums[i]);
					InflowAnalysis.addDomainElements(taintMap, block, flowType, CodeElement.valueElements(pa, node, node.getIR().getParameter(newArgNums[i])));
				}
			}
			break;			
		case BINDER_SOURCE:
			for (int i = 0; i < newArgNums.length; i++) {

				for(InstanceKey ik: pa.getPointsToSet(new LocalPointerKey(node,node.getIR().getParameter(newArgNums[i]))))
				{
					FlowType flow = new BinderFlow(ik, node, name, im.getSignature(), newArgNums[i]);

					for (BasicBlockInContext<E> block : graph.getEntriesForProcedure(node) ){
						InflowAnalysis.addDomainElement(taintMap, block, flow, new InstanceKeyElement(ik));
						InflowAnalysis.addDomainElement(taintMap, block, flow, new LocalElement(node.getIR().getParameter(newArgNums[i])));
					}
				}
			}
			break;
		default:
    		throw new UnsupportedOperationException("SourceType not yet Implemented");        			
		}

	}
}
