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

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import util.IFDSTaintFlowFunctionProvider;
import util.LoaderUtils;
import util.MyLogger;
import util.MyLogger.LogLevel;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.ibm.wala.classLoader.IClass;
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
import com.ibm.wala.ipa.callgraph.propagation.InstanceFieldKey;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.LocalPointerKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ipa.callgraph.propagation.StaticFieldKey;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
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
	        Maps.newHashMap();
	
	//contains a mapping from an IMethod to a mapping of Integers
	//(which represent the parameter #) to their respective instancekey set
	public final Map<IMethod, Map<Integer, OrdinalSet<InstanceKey>>> methodTaints = 
	        Maps.newHashMap();
	
	private final Set<MethodReference> blacklist = Sets.newHashSet();
			
	
	private Predicate<IMethod> isConstructor = new Predicate<IMethod>() {
		@Override
		public boolean test(IMethod t) {
			return t.isInit();
		}
	};
	
	private Predicate<IMethod> p = new Predicate<IMethod>(){
			@Override
			public boolean test(IMethod im) {
				if (im.isSynthetic())
					return false;
				if (newSummaries.containsKey(im))
					return false;
				return true;
			}
		}.and(isConstructor.not()); // the summary files can't summarize constructors.
	
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

		Map<FlowType<E>, Set<CodeElement>> methodFlows = Maps.newHashMap();
		newSummaries.put(entryMethod, methodFlows);

		// Map from parameter number to the points to set for that param:
		Map<Integer, OrdinalSet<InstanceKey>> pTaintIKMap = Maps.newHashMap();
		methodTaints.put(entryMethod, pTaintIKMap);

		final List<PathEdge<BasicBlockInContext<E>>>
		         initialEdges = Lists.newArrayList();
		
		Set<DomainElement> initialTaints = new HashSet<DomainElement> ();		

		// Add PathEdges to the initial taints.  
		// In this case, taint all parameters into the method call
		for (int i = 0; i < entryMethod.getNumberOfParameters(); i++) {
			//int id = entryMethod.isStatic()?i:i+1;
			OrdinalSet<InstanceKey> pointsToSet = 
			        pa.getPointsToSet(
			                new LocalPointerKey(methEntryBlock.getNode(),
			                		methEntryBlock.getNode().getIR().getParameter(i)
			                        ));

            pTaintIKMap.put(i, pointsToSet);

			DomainElement de = new DomainElement(new LocalElement(i + 1),
			                         new ParameterFlow<E>(methEntryBlock, i, true));
			initialTaints.add(de);

			// taint the parameter:
			int taint = domain.getMappedIndex(de);
			initialEdges.add(PathEdge.createPathEdge(methEntryBlock, 0, methEntryBlock, 
					taint));
			
			// taint the fields on the parameter:
			TypeReference paramTR = entryMethod.getParameterType(i);
			IClass paramClass = pa.getClassHierarchy().lookupClass(paramTR);
			
			if (paramTR.isPrimitiveType() 
			 || paramTR.isArrayType()
			 || paramClass == null ) {
				continue;
			}
			
			Collection<IField> fields = paramClass.getAllFields();
			
			for (IField iField : fields) {
				taintField(pa, iField, pointsToSet, methEntryBlock, domain,
						initialTaints, initialEdges, Sets.newHashSet(paramTR));
			}
		}

		if (entryMethod.isStatic()) {
			// we need to taint the static fields of the enclosing class.
			// if the method is *not* static, then these will have been tainted
			// during the tainting of parameters above.
			for (IField field : entryMethod.getDeclaringClass().getAllStaticFields() ){
				Iterable<InstanceKey> staticInstances = 
						pa.getPointsToSet(new StaticFieldKey(field));
				
				taintField(pa, field, staticInstances, 
						methEntryBlock, domain, initialTaints,
						initialEdges, Sets.<TypeReference>newHashSet());
			}
		}

		// TODO we don't currently taint static globals outside of the enclosing
		// class / method params.

		
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

	private void taintField(PointerAnalysis pa, 
			IField myField,
			Iterable<InstanceKey> parentPointsToSet,
			BasicBlockInContext<E> methEntryBlock,
			IFDSTaintDomain<E> domain,
			Set<DomainElement> initialTaints,
			List<PathEdge<BasicBlockInContext<E>>> initialEdges,
			Set<TypeReference> taintedTypes) {

	    TypeReference tr = myField.getFieldTypeReference();
	    
	    if ( taintedTypes.contains(tr) ){
	    	//MyLogger.log(LogLevel.DEBUG, "*not* re-tainting tainted type: "+myField);
	    	return;
	    }
	    
	    //MyLogger.log(LogLevel.DEBUG, "tainting field: "+myField);
	    taintedTypes.add(tr);
		
	    Collection<PointerKey> pointerKeys = Lists.newArrayList();
	    if (myField.isStatic()) {
	        pointerKeys.add(new StaticFieldKey(myField));
	    } else {
	    	for (InstanceKey ik : parentPointsToSet) {
	    		pointerKeys.add(new InstanceFieldKey(ik, myField));
	    	}
	    } 

    	for (InstanceKey ik: parentPointsToSet) {
			DomainElement de = new DomainElement(
			        new FieldElement(ik, myField.getReference(), myField.isStatic()), 
					new FieldFlow<E>(methEntryBlock, myField, true));
			initialTaints.add(de);
			initialEdges.add(PathEdge.createPathEdge(methEntryBlock, 0, methEntryBlock, 
					domain.getMappedIndex(de)));
    	}
    	
    	// We need the all the instance keys for the field we're currently 
	    // tainting for the recursive case
	    List<InstanceKey> iks = Lists.newArrayList();
	    for (PointerKey pk : pointerKeys ) {
	    	for (InstanceKey ik: pa.getPointsToSet(pk)) {
				iks.add(ik);
			}
	    }
	    
	    IClassHierarchy cha = myField.getClassHierarchy();
	    IClass fieldClass = cha.lookupClass(tr);
	    
	    // recurse on the fields of the myField:
	    
	    // Terminate recursion if myField is a primitive or an array
	    // because they don't have fields.
	    // Also, if the type is in the exclusions file (or doesn't exist for 
	    // some other reason...) then the class reference will be null.
	    if (tr.isPrimitiveType() 
	     || tr.isArrayType() 
	     || fieldClass == null ) {
	    	return;
	    }
	    
		Collection<IField> fields = fieldClass.getAllFields();
	    
		for(IField field : fields) {
			taintField(pa, field, iks, methEntryBlock, domain, 
					initialTaints, initialEdges, taintedTypes);
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
				
//				System.out.println(de.taintSource + " FLOWS into " + de.codeElement);
				
				if (de.codeElement instanceof FieldElement) {
				    // TODO make sure this covers static fields too.
//					MyLogger.log(DEBUG,de.taintSource +" FLOWS into FIELD " + de.codeElement);
					addToFlow(de.taintSource, de.codeElement, methodFlows);
				} else if (de.codeElement instanceof ReturnElement) {
				    
//					MyLogger.log(DEBUG,de.taintSource + " FLOWS into RETURNELEMENT " + de.codeElement);
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
