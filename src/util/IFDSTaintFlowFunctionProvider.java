/*
 *
 * Copyright (c) 2009-2012,
 *
 *  Adam Fuchs          <afuchs@cs.umd.edu>
 *  Avik Chaudhuri      <avik@cs.umd.edu>
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

package util;
import static util.MyLogger.LogLevel.DEBUG;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import synthMethod.MethodAnalysis;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.dataflow.IFDS.IFlowFunction;
import com.ibm.wala.dataflow.IFDS.IFlowFunctionMap;
import com.ibm.wala.dataflow.IFDS.ISupergraph;
import com.ibm.wala.dataflow.IFDS.IUnaryFlowFunction;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.LocalPointerKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ipa.callgraph.propagation.StaticFieldKey;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ipa.summaries.XMLMethodSummaryReader;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAArrayLoadInstruction;
import com.ibm.wala.ssa.SSAArrayStoreInstruction;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.intset.BitVectorIntSet;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.OrdinalSet;

import domain.CodeElement;
import domain.DomainElement;
import domain.FieldElement;
import domain.IFDSTaintDomain;
import domain.InstanceKeyElement;
import domain.LocalElement;
import domain.ReturnElement;
import flow.types.FlowType;

public class IFDSTaintFlowFunctionProvider<E extends ISSABasicBlock>
implements IFlowFunctionMap<BasicBlockInContext<E>> {

	private final IFDSTaintDomain<E> domain;
	private final ISupergraph<BasicBlockInContext<E>,CGNode> graph;
	private final PointerAnalysis pa;
	private final MethodAnalysis<E> methodAnalysis;

	public IFDSTaintFlowFunctionProvider(IFDSTaintDomain<E> domain,
			ISupergraph<BasicBlockInContext<E>, CGNode> graph, 
			PointerAnalysis pa,
			MethodAnalysis<E> methodAnalysis)
	{
		this.domain = domain;
		this.graph = graph;
		this.pa = pa;
		this.methodAnalysis = methodAnalysis;
	}

	// instruction has a valid def set
	private static boolean inFlow(SSAInstruction instruction) {
		return
				(instruction instanceof SSAArrayLoadInstruction) ||
				(instruction instanceof SSAGetInstruction);
	}

	// instruction's def is getUse(0)
	private static boolean outFlow(SSAInstruction instruction) {
		return
				(instruction instanceof SSAArrayStoreInstruction) ||
				(instruction instanceof SSAPutInstruction) ||
				(instruction instanceof SSAInvokeInstruction);
	}

	// instruction is a return instruction
	private static boolean returnFlow(SSAInstruction instruction) {
		return (instruction instanceof SSAReturnInstruction);
	}

	private static class UseDefSetPair
	{
		public Set<CodeElement> uses = new HashSet<CodeElement>();
		public Set<CodeElement> defs = new HashSet<CodeElement>();
	}

	public class DefUse implements IUnaryFlowFunction
	{
		final ArrayList<UseDefSetPair> useToDefList = new ArrayList<UseDefSetPair>();

		final BasicBlockInContext<E> bb;

		public DefUse(final BasicBlockInContext<E> bb)
		{
			this.bb = bb;
			Iterator<SSAInstruction> instructions = bb.iterator();
			while (instructions.hasNext()) {
				SSAInstruction instruction = instructions.next();
				UseDefSetPair p = new UseDefSetPair();
				boolean thisToResult = false;
				if(instruction instanceof SSAInvokeInstruction)
				{
					SSAInvokeInstruction invInst = (SSAInvokeInstruction)instruction;
					if(!invInst.isSpecial() && !invInst.isStatic() && instruction.getNumberOfDefs() > 0)
					{
						//System.out.println("adding receiver flow in "+this+" for "+invInst);
						//System.out.println("\tadding local element "+invInst.getReceiver());
						//getReceiver() == getUse(0) == param[0] == this
						p.uses.addAll(CodeElement.valueElements(pa, bb.getNode(), invInst.getReceiver()));
						for(int i = 0; i < invInst.getNumberOfDefs(); i++)
						{
							//System.out.println("\tadding def local element "+invInst.getDef(i));
							//return valuenumber of invoke instruction
							p.defs.addAll(CodeElement.valueElements(pa, bb.getNode(), invInst.getDef(i)));
						}
					}
					thisToResult = true;
				}
				if (thisToResult) {
					useToDefList.add(p);
					p = new UseDefSetPair();
				}
					
			    IClassHierarchy ch = bb.getNode().getClassHierarchy();

				if (inFlow(instruction)) {
					if (instruction instanceof SSAGetInstruction) {
						SSAGetInstruction gi = (SSAGetInstruction)instruction;
						
						PointerKey pk;
						FieldReference declaredField = gi.getDeclaredField();
                        if ( gi.isStatic()) {
						    IField staticField =
                                    getStaticIField(ch, declaredField);

						    pk = new StaticFieldKey(staticField);
						} else {
						    int valueNumber = instruction.getUse(0);
						    pk = new LocalPointerKey(bb.getNode(), valueNumber);
						}
						
						Set<CodeElement> elements = new HashSet<CodeElement>();
						OrdinalSet<InstanceKey> m = pa.getPointsToSet(pk);
						if(m != null) {
							for(Iterator<InstanceKey> keyIter = m.iterator();keyIter.hasNext();) {
								elements.add(
								        new FieldElement(keyIter.next(), declaredField));
							}
						}
						p.uses.addAll(elements);
						//getinstruction only has 1 def
						p.defs.add(new LocalElement(instruction.getDef(0)));
					}
					else {
						//getuse() is result value for getfield.  arrayref for arrayload
						for (int i = 0; i < instruction.getNumberOfUses(); i++) {
							p.uses.addAll(CodeElement.valueElements(pa, bb.getNode(), instruction.getUse(i)));
						}
						//getdef() is result value
						for (int j = 0; j < instruction.getNumberOfDefs(); j++) {
							/* TODO: why not add instance keys, too? */
							p.defs.add(new LocalElement(instruction.getDef(j)));
						}
					}
				}
				else if (outFlow(instruction)) {
					if (instruction instanceof SSAPutInstruction) {
						SSAPutInstruction pi = (SSAPutInstruction)instruction;
						PointerKey pk;

						Set<CodeElement> elements = new HashSet<CodeElement>();
						if (pi.isStatic()) {
						    p.uses.addAll(CodeElement.valueElements(pa, bb.getNode(), instruction.getUse(0)));
						    FieldReference declaredField = pi.getDeclaredField();
                            IField staticField = getStaticIField(ch, declaredField);
						    pk = new StaticFieldKey(staticField);
						} else {
						    p.uses.addAll(CodeElement.valueElements(pa, bb.getNode(), instruction.getUse(1)));
							int valueNumber = instruction.getUse(0);
							pk = new LocalPointerKey(bb.getNode(), valueNumber);
						}	
                        OrdinalSet<InstanceKey> m = pa.getPointsToSet(pk);
                        if (m != null) {
                            for (Iterator<InstanceKey> keyIter = m.iterator(); keyIter
                                    .hasNext();) {
                                elements.add(new FieldElement(keyIter.next(),
                                        pi.getDeclaredField()));
                            }
                        }
						p.defs.addAll(elements);
					}
					else if (instruction instanceof SSAArrayStoreInstruction){						
						p.uses.addAll(CodeElement.valueElements(pa, bb.getNode(), instruction.getUse(2)));
						p.defs.addAll(CodeElement.valueElements(pa, bb.getNode(), instruction.getUse(0)));
					}
					else {
						for (int i = 1; i < instruction.getNumberOfUses(); i++) {
							p.uses.addAll(CodeElement.valueElements(pa, bb.getNode(), instruction.getUse(i)));
						}
						if (instruction.getNumberOfUses() > 0) {
							p.defs.addAll(CodeElement.valueElements(pa, bb.getNode(), instruction.getUse(0)));
						}
					}
				}
				else if(returnFlow(instruction))
				{
					SSAReturnInstruction retInst = (SSAReturnInstruction)instruction;
					if(retInst.getNumberOfUses() > 0)
					{
						/* TODO: why not add instance keys, too? */
								for(int i = 0; i < instruction.getNumberOfUses(); i++)
								{
									p.uses.add(new LocalElement(instruction.getUse(i)));
								}
								p.defs.add(new ReturnElement());
					}
					else
					{
						continue;
					}
				}
				else
				{
					continue;
				}
				useToDefList.add(p);
			}
		}

        private IField getStaticIField(IClassHierarchy ch,
                FieldReference declaredField) {
            TypeReference staticTypeRef = declaredField.getDeclaringClass();
            Set<IClass> classes = ch.getImplementors(staticTypeRef);
            
            assert classes.size() == 1 : 
                "Too many classes found for static ref.";
            
            IClass staticClass = classes.iterator().next();
            IField staticField = 
                    staticClass.getField(declaredField.getName());
            return staticField;
        }

		void addTargets(CodeElement d1, BitVectorIntSet set, FlowType taintType)
		{
			//System.out.println(this.toString()+".addTargets("+d1+"...)");
			for(UseDefSetPair p: useToDefList)
			{
				if(p.uses.contains(d1))
				{
					//System.out.println("\t\tfound pair that uses "+d1);
					for(CodeElement i:p.defs)
					{
						//System.out.println("\t\tadding outflow "+i);
						set.add(domain.getMappedIndex(new DomainElement(i,taintType)));
					}
				}
			}
		}	

		public IntSet getTargets(int d1) {
			//System.out.println(this.toString()+".getTargets("+d1+") "+bb);
			BitVectorIntSet set = new BitVectorIntSet();
			set.add(d1);
			DomainElement de = domain.getMappedObject(d1);
			if (de != null)
				addTargets(de.codeElement, set, de.taintSource);
			return set;
		}
	}

	public IUnaryFlowFunction getCallFlowFunction(BasicBlockInContext<E> src,
			BasicBlockInContext<E> dest,
			BasicBlockInContext<E> ret) {
		assert graph.isCall(src);

		final SSAInvokeInstruction instruction = (SSAInvokeInstruction) src.getLastInstruction();

		if (! dest.getMethod().isSynthetic() 
		    && LoaderUtils.fromLoader(dest.getNode(), ClassLoaderReference.Primordial)) {
		    
            MyLogger.log(DEBUG,"Primordial and No Summary! (getCallFlowFunction) - " + dest.getMethod().getReference());
            methodAnalysis.analyze(graph, pa, dest);
            dest.getMethod().isSynthetic();
		}
		

		final Map<CodeElement,CodeElement> parameterMap = new HashMap<CodeElement,CodeElement>();
		for (int i = 0; i < instruction.getNumberOfParameters(); i++) {
			Set<CodeElement> elements = CodeElement.valueElements(pa, src.getNode(), instruction.getUse(i));
			for(CodeElement e: elements) {
				parameterMap.put(e, new LocalElement(i+1));
			}
		}

		return new IUnaryFlowFunction() {

			public IntSet getTargets(int d1) {
				BitVectorIntSet set = new BitVectorIntSet();
				if(d1 == 0 || !(domain.getMappedObject(d1).codeElement instanceof LocalElement))
					set.add(d1);
				DomainElement de = domain.getMappedObject(d1);
				if(de!=null && parameterMap.containsKey(de.codeElement))
					set.add(domain.getMappedIndex(new DomainElement(parameterMap.get(de.codeElement),de.taintSource)));
				return set;
			}

		};
	}

	public IUnaryFlowFunction getCallNoneToReturnFlowFunction(
			BasicBlockInContext<E> src,
			BasicBlockInContext<E> dest) {
		//I Believe this method is called only if there are no callees of src in the supergraph
		//if supergraph included all primordials, this method can still be called if it calls a 		
		//method that wasn't included in the scope
		
		//Assertions.UNREACHABLE();
		// TODO: Look up summary for this method, or warn if it doesn't exist.
		assert (src.getNode().equals(dest.getNode()));
		
		final SSAInvokeInstruction instruction = (SSAInvokeInstruction) src.getLastInstruction();

		System.out.println("call to return(no callee) method inside call graph: " + src.getNode()+"--" + instruction.getDeclaredTarget());
		return new DefUse(dest);
	}

	public IUnaryFlowFunction getCallToReturnFlowFunction(
			BasicBlockInContext<E> src,
			BasicBlockInContext<E> dest) {
		assert (src.getNode().equals(dest.getNode()));
		//final SSAInvokeInstruction instruction = (SSAInvokeInstruction) src.getLastInstruction();
		//System.out.println("call to return method inside call graph: " + instruction.getDeclaredTarget());

		return new DefUse(dest);
	}

	public IUnaryFlowFunction getNormalFlowFunction(
			BasicBlockInContext<E> src,
			BasicBlockInContext<E> dest) {
		assert (src.getNode().equals(dest.getNode()));
		//System.out.println("getNormalFlowFuntion");
		//System.out.println("\tSrc " + src.getLastInstruction());
		//System.out.println("\tDest " + dest.getLastInstruction());
		return new DefUse(dest);
	}

	public class ReturnDefUse extends DefUse
	{
		CodeElement callSet;
		Set<CodeElement> receivers = new HashSet<CodeElement>();

		public ReturnDefUse(BasicBlockInContext<E> dest,
				BasicBlockInContext<E> call) {
			super(dest);
			
			// TODO: look into exception handling through getDef(1)
			if(call.getLastInstruction() instanceof SSAInvokeInstruction) {
				SSAInvokeInstruction invInst = (SSAInvokeInstruction) call.getLastInstruction();
				if(!invInst.isSpecial() && !invInst.isStatic()) {
//					for (int i = 0; i < invInst.getNumberOfReturnValues(); i++) {
//						
//					}
					if (invInst.hasDef()) {
						callSet = new LocalElement(invInst.getReturnValue(0));

						//used to be invInst.getReceiver(), but I believe that was incorrect.
						receivers.addAll(CodeElement.valueElements(pa, call.getNode(), invInst.getReturnValue(0)));
					}
				}				
			}
			else
				callSet = null;
			
//			// TODO: look into exception handling through getDef(1)
//			if(call.getLastInstruction().getNumberOfDefs() == 1)
//			{
//				//System.out.println("\treturn defines something: "+call.getLastInstruction());
//				callSet = new LocalElement(call.getLastInstruction().getDef(0));
//				if(call.getLastInstruction() instanceof SSAInvokeInstruction)
//				{
//					SSAInvokeInstruction invInst = (SSAInvokeInstruction) call.getLastInstruction();
//					if(!invInst.isSpecial() && !invInst.isStatic()) {
//						receivers.addAll(CodeElement.valueElements(pa, call.getNode(), invInst.getReceiver()));
//					}
//				}
//			}
//			else
//				callSet = null;
		}

		@Override
		public IntSet getTargets(int d1)
		{
			if(d1 != 0 && domain.getMappedObject(d1).codeElement instanceof ReturnElement)
			{
				BitVectorIntSet set = new BitVectorIntSet();
				if(callSet != null) {
//					System.out.println("callset: " + callSet);
					set.add(domain.getMappedIndex(new DomainElement(callSet,domain.getMappedObject(d1).taintSource)));
				}
				return set;
			}
			else if(d1 != 0 && domain.getMappedObject(d1).codeElement instanceof LocalElement)
			{
				return new BitVectorIntSet();
			}
			else if(d1 != 0 && receivers.contains(domain.getMappedObject(d1).codeElement))
			{
				BitVectorIntSet set = new BitVectorIntSet();
				if(callSet != null)
					set.add(domain.getMappedIndex(new DomainElement(callSet,domain.getMappedObject(d1).taintSource)));
				set.addAll(super.getTargets(d1));
				return set;
			}
			else
			{
				return super.getTargets(d1);
			}
		}
	}

	public IFlowFunction getReturnFlowFunction(BasicBlockInContext<E> call,
			BasicBlockInContext<E> src,
			BasicBlockInContext<E> dest) {
		assert (graph.isCall(call) && graph.isReturn(dest) && call.getNode().equals(dest.getNode()));
		//final SSAInvokeInstruction instruction = (SSAInvokeInstruction) call.getLastInstruction();

		//System.out.println("Return from call to method inside call graph: " + instruction.getDeclaredTarget());

		return new ReturnDefUse(dest,call);
	}

}
