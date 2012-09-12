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


package synthMethod;

import static util.MyLogger.LogLevel.DEBUG;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import util.IFDSTaintFlowFunctionProvider;
import util.LoaderUtils;
import util.MyLogger;

import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.dataflow.IFDS.IFlowFunctionMap;
import com.ibm.wala.dataflow.IFDS.IMergeFunction;
import com.ibm.wala.dataflow.IFDS.ISupergraph;
import com.ibm.wala.dataflow.IFDS.PathEdge;
import com.ibm.wala.dataflow.IFDS.TabulationDomain;
import com.ibm.wala.dataflow.IFDS.TabulationProblem;
import com.ibm.wala.dataflow.IFDS.TabulationResult;
import com.ibm.wala.dataflow.IFDS.TabulationSolver;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.LocalPointerKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ipa.callgraph.propagation.StaticFieldKey;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.CancelRuntimeException;
import com.ibm.wala.util.Predicate;
import com.ibm.wala.util.intset.IntIterator;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.OrdinalSet;

import domain.CodeElement;
import domain.DomainElement;
import domain.FieldElement;
import domain.IFDSTaintDomain;
import domain.LocalElement;
import domain.ReturnElement;
import flow.types.FieldFlow;
import flow.types.FlowType;
import flow.types.ParameterFlow;


public class MethodAnalysis <E extends ISSABasicBlock>  {
	//contains a mapping from an IMethod to a mapping of flows
	//parameters and fields to returns or other fields.
	public final Map<IMethod, Map<FlowType<E>, Set<CodeElement>>> newSummaries =
	        new HashMap<IMethod, Map<FlowType<E>, Set<CodeElement>>>();
	//contains a mapping from an IMethod to a mapping of Integers
	//(which represent the parameter #) to their respective instancekey set
	public final Map<IMethod, Map<Integer, OrdinalSet<InstanceKey>>> methodTaints = 
	        new HashMap<IMethod, Map<Integer, OrdinalSet<InstanceKey>>>();
	
	private final Set<MethodReference> blacklist = new HashSet<MethodReference>();
	    
	private Predicate<IMethod> p = new Predicate<IMethod>(){
			@Override
			public boolean test(IMethod im) {
				if (im.isSynthetic())
					return false;
				if (newSummaries.containsKey(im))
					return false;
				return true;
			}
		};
	
	private ClassLoaderReference clr;
	
	public MethodAnalysis() {
	    this.p = this.p.and(new Predicate<IMethod>() {
            @Override
            public boolean test(IMethod im) {
                return LoaderUtils.fromLoader(im, ClassLoaderReference.Primordial);
            }
	    });
	}
	
	public MethodAnalysis(Predicate<IMethod> pred) {
		this.p = this.p.and(pred);
	}
	
	private boolean shouldSummarize(IMethod entryMethod) {
		return p.test(entryMethod);
	}
	
	/**
	 * Summarize a method.
	 * 
	 * @param graph
	 * @param pa
	 * @param callerBlock The block where this method is *invoked*.  This should
	 *        be set to null if you are invoking this method directly -- the mutual recursion 
	 *        that causes subsequent invocations will provide caller blocks for the
	 *        purposes of tracking the blacklist. 
	 * @param methEntryBlock  The entry block for the method that is to be summarized.
	 * @throws CancelRuntimeException
	 */
	public void analyze(
            final ISupergraph<BasicBlockInContext<E>, CGNode> graph,
            final PointerAnalysis pa,
            final BasicBlockInContext<E> callerBlock,
            final BasicBlockInContext<E> methEntryBlock // TODO make this into a node or IMethod
            ) throws CancelRuntimeException {
		
		boolean DEBUG = false;
		if (DEBUG) {
			String signature = methEntryBlock.getMethod().getSignature();
			System.out.print("   Method Analysis working on: "+signature);
		}
        final IFDSTaintDomain<E> domain = new IFDSTaintDomain<E>();
    
        IMethod entryMethod = methEntryBlock.getMethod();

		if (!shouldSummarize(entryMethod)) {
			if (DEBUG) {
				System.out.println(" (but not summarizing)");
			}
			return;
		} else {
			if (DEBUG) {
				System.out.println();
			}
		}

		Map<FlowType<E>, Set<CodeElement>> methodFlows = new HashMap<FlowType<E>, Set<CodeElement>>();
		newSummaries.put(entryMethod, methodFlows);

		Map<Integer, OrdinalSet<InstanceKey>> pTaintIKMap = new HashMap<Integer, OrdinalSet<InstanceKey>> ();
		methodTaints.put(entryMethod, pTaintIKMap);

		final List<PathEdge<BasicBlockInContext<E>>>
		         initialEdges = new ArrayList<PathEdge<BasicBlockInContext<E>>>();
		
		Set<DomainElement> initialTaints = new HashSet<DomainElement> ();		

		// Add PathEdges to the initial taints.  
		// In this case, taint all parameters into the method call
		for (int i = 0; i < entryMethod.getNumberOfParameters(); i++) {
			//int id = entryMethod.isStatic()?i:i+1;

			DomainElement de = new DomainElement(new LocalElement(i + 1),
			                         new ParameterFlow<E>(methEntryBlock, i, true));
			
			OrdinalSet<InstanceKey> pointsToSet = 
			        pa.getPointsToSet(
			                new LocalPointerKey(methEntryBlock.getNode(),
			                        methEntryBlock.getNode().getIR().getParameter(i)
			                        ));
                			//new LocalPointerKey(callerBlock.getNode(), invInst.getUse(i)));
            pTaintIKMap.put(i, pointsToSet);
			
//							CodeElement.valueElements(pa, methEntryBlock.getNode(), invInst.getUse(i))));
			initialTaints.add(de);
			initialEdges.add(PathEdge.createPathEdge(methEntryBlock, 0, methEntryBlock, 
					domain.getMappedIndex(de)));
		}

		// Also taint all field elements
		for (IField myField : entryMethod.getDeclaringClass().getAllFields()) {
		    PointerKey pk;
		    boolean isStatic;
		    if (myField.isStatic()) {
		        pk = new StaticFieldKey(myField);
		        isStatic = true;
		    } else if (!entryMethod.isStatic()) {
		        pk = new LocalPointerKey(methEntryBlock.getNode(),
		                methEntryBlock.getNode().getIR().getParameter(0));
		        isStatic = false;
		    } else {
		        /* do nothing if the method is static and the field is not --
		         * static methods can't access non-static fields.
		         */
		        continue;
		    }
		    
		    for (InstanceKey ik: pa.getPointsToSet(pk)) {
				DomainElement de = new DomainElement(
				        new FieldElement(ik, myField.getReference(), isStatic), 
						new FieldFlow<E>(methEntryBlock, myField, true));
				initialTaints.add(de);
				initialEdges.add(PathEdge.createPathEdge(methEntryBlock, 0, methEntryBlock, 
						domain.getMappedIndex(de)));
			}
			
		}

		final IFlowFunctionMap<BasicBlockInContext<E>> functionMap =
				new IFDSTaintFlowFunctionProvider<E>(domain, graph, pa, this);

		final TabulationProblem<BasicBlockInContext<E>, CGNode, DomainElement>
		   problem =
		     new TabulationProblem<BasicBlockInContext<E>, CGNode, DomainElement>() {

			public TabulationDomain<DomainElement, BasicBlockInContext<E>> getDomain() {
				return domain;
			}

			public IFlowFunctionMap<BasicBlockInContext<E>> getFunctionMap() {
				return functionMap;
			}

			public IMergeFunction getMergeFunction() {
				return null;
			}

			public ISupergraph<BasicBlockInContext<E>, CGNode> getSupergraph() {
				return graph;
			}

			public Collection<PathEdge<BasicBlockInContext<E>>> initialSeeds() {
				return initialEdges;
			}
		};
		TabulationSolver<BasicBlockInContext<E>, CGNode, DomainElement> solver =
				TabulationSolver.make(problem);

		try {
			TabulationResult<BasicBlockInContext<E>,CGNode, DomainElement> flowResult = solver.solve();
			if (blacklist.contains(methEntryBlock.getMethod().getReference()))
				blacklist.add(callerBlock.getMethod().getReference());
			else
				checkResults(domain, flowResult, initialTaints, graph, methEntryBlock, methodFlows);

			//       	if (CLI.hasOption("IFDS-Explorer")) {
			//       		for (int i = 1; i < domain.getSize(); i++) {        			
			//                   MyLogger.log(DEBUG,"DomainElement #"+i+" = " + domain.getMappedObject(i));        			
			//       		}
			//       		GraphUtil.exploreIFDS(flowResult);
			//       	}
//			return flowResult;
		} catch (CancelException e) {
			throw new CancelRuntimeException(e);
		}
	}

	private static<E extends ISSABasicBlock> void checkResults(
	        IFDSTaintDomain<E> domain,
			TabulationResult<BasicBlockInContext<E>,CGNode, DomainElement> flowResult, 
			Set<DomainElement> initialTaints,
			ISupergraph<BasicBlockInContext<E>, CGNode> graph, 
			BasicBlockInContext<E> methEntryBlock, 
			Map<FlowType<E>, Set<CodeElement>> methodFlows) {
		MyLogger.log(DEBUG,"***************");	 
		MyLogger.log(DEBUG,"Method Analysis");
		MyLogger.log(DEBUG,methEntryBlock.getMethod().getSignature());
		MyLogger.log(DEBUG,"***************");


		Set<FlowType<E>> initialFlowSet = new HashSet<FlowType<E>> ();
		for (DomainElement de:initialTaints) {
			initialFlowSet.add(de.taintSource);
		}
				
		
		BasicBlockInContext<E> exitBlocks[] = graph.getExitsForProcedure(methEntryBlock.getNode());
		
		System.out.println("     exitBlock count: "+exitBlocks.length);
		for (BasicBlockInContext<E> exitBlock:exitBlocks) {
			IntSet exitResults = flowResult.getResult(exitBlock);
			
//			System.out.println("     exitResult count: "+exitResults.size());
			
			for (IntIterator intI = exitResults.intIterator(); intI.hasNext();) {
				int i = intI.next();
				DomainElement de = domain.getMappedObject(i);
				assert (de != null);
				
				//Ignore parameters flowing to itself.  And Fields flowing to itself.
				//Also only take into consideration flows which originate from the current 
				//method we are summarizing
				if (initialTaints.contains(de)) {
//					System.out.println("     initialTaints contains domain element: "+de);
					continue;
				}
				if (!initialFlowSet.contains(de.taintSource)) {
//					System.out.println("     initialFlowSet does not contain domain element: "+de);
					continue;
				}
				
				System.out.println(de.taintSource + " FLOWS into " + de.codeElement);
				
				if (de.codeElement instanceof FieldElement) {
				    // TODO make sure this covers static fields too.
					MyLogger.log(DEBUG,de.taintSource +" FLOWS into FIELD " + de.codeElement);
					addToFlow(de.taintSource, de.codeElement, methodFlows);
				} else if (de.codeElement instanceof ReturnElement) {
				    
					MyLogger.log(DEBUG,de.taintSource + " FLOWS into RETURNELEMENT " + de.codeElement);
					addToFlow(de.taintSource, de.codeElement, methodFlows);
				}
			}
		}

//		IClass myClass = methEntryBlock.getMethod().getDeclaringClass();
//		String classloadername = myClass.getReference().getClassLoader().getName().toString();
//		String classname = myClass.getName().getClassName().toString();
//		String packagename = myClass.getName().getPackage().toString();
//		String methodname = methEntryBlock.getMethod().getName().toString();
//		String descriptor = methEntryBlock.getMethod().getDescriptor().toString();
//
//		System.out.println("classloader: " + classloadername);
//		System.out.println("class: " + classname);
//		System.out.println("package: " + packagename);
//		System.out.println("method: " + methodname);
//		System.out.println("descriptor: " + descriptor);

	}

	public static <E extends ISSABasicBlock> 
	void addToFlow (FlowType<E> ft, 
	                CodeElement ce,  
	                Map<FlowType<E>, Set<CodeElement>> methodFlows) {
		Set<CodeElement> ceSet = methodFlows.get(ft);
		if (ceSet == null) {
			ceSet = new HashSet<CodeElement>();
			methodFlows.put(ft,  ceSet);
		}
		ceSet.add(ce);
	}


}
