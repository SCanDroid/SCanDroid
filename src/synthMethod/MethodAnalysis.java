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
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import util.AndroidAppLoader;
import util.CLI;
import util.GraphUtil;
import util.IFDSTaintFlowFunctionProvider;
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
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ipa.summaries.XMLMethodSummaryReader;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.CancelRuntimeException;
import com.ibm.wala.util.intset.IntIterator;
import com.ibm.wala.util.intset.IntSet;

import domain.CodeElement;
import domain.DomainElement;
import domain.FieldElement;
import domain.IFDSTaintDomain;
import domain.LocalElement;
import domain.ReturnElement;
import flow.types.FieldFlow;
import flow.types.FlowType;
import flow.types.ParameterFlow;


public class MethodAnalysis {

   public static <E extends ISSABasicBlock> TabulationResult<BasicBlockInContext<E>, CGNode, DomainElement> analyze(IFDSTaintDomain<E> d, 
		   final ISupergraph<BasicBlockInContext<E>, CGNode> graph, PointerAnalysis pa, XMLMethodSummaryReader methodSummaryReader,
		   BasicBlockInContext<E> callerBlock, BasicBlockInContext<E> methEntryBlock) throws CancelRuntimeException {

	   assert(callerBlock.getLastInstruction() instanceof SSAInvokeInstruction);
	   SSAInvokeInstruction invInst = (SSAInvokeInstruction)callerBlock.getLastInstruction();

       final IFDSTaintDomain<E> domain = d;

       final ArrayList<PathEdge<BasicBlockInContext<E>>>
          initialEdges = new ArrayList();
  
       IMethod entryMethod = methEntryBlock.getMethod();
       
       Set<DomainElement> initialTaints = new HashSet<DomainElement> ();
       
       //Add PathEdges to the initial taints.  In this case, taint all parameters into the method call
       for (int i = 0; i < entryMethod.getNumberOfParameters(); i++) {
    	   DomainElement de = new DomainElement(new LocalElement(i+1), new ParameterFlow(entryMethod.getReference(),i+1));
    	   initialTaints.add(de);
           initialEdges.add(PathEdge.createPathEdge(methEntryBlock, 0, methEntryBlock, 
        		   domain.getMappedIndex(de)));
       }
       //Also taint all field elements
       for (IField myField : entryMethod.getDeclaringClass().getAllFields()) {
    	   DomainElement de = new DomainElement(new FieldElement(entryMethod.getDeclaringClass().getReference(),
				   myField.getReference().getSignature()),
				   new FieldFlow(entryMethod.getDeclaringClass().getReference(),
        				   myField.getReference().getSignature()));
    	   initialTaints.add(de);
    	   initialEdges.add(PathEdge.createPathEdge(methEntryBlock, 0, methEntryBlock, 
        		   domain.getMappedIndex(de)));
       }

       

       final IFlowFunctionMap<BasicBlockInContext<E>> functionMap =
           new IFDSTaintFlowFunctionProvider<E>(domain, graph, pa, methodSummaryReader);
       
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
//             CGNode entryProc = cfg.getCallGraph().getEntrypointNodes()
//                     .iterator().next();
//             BasicBlockInContext<ISSABasicBlock> entryBlock = cfg
//                     .getEntry(entryProc);
//             for (int i = 0; i < entryProc.getIR().getNumberOfParameters(); i++) {
//                 list.add(PathEdge.createPathEdge(entryBlock, 0, entryBlock,
//                         domain.getMappedIndex(new LocalElement(i + 1))));
//             }
//             return list;
           }

       };
       TabulationSolver<BasicBlockInContext<E>, CGNode, DomainElement> solver =
           TabulationSolver.make(problem);

       try {
       	TabulationResult<BasicBlockInContext<E>,CGNode, DomainElement> flowResult = solver.solve();
       	checkResults(domain, flowResult, initialTaints, graph, methEntryBlock);
//       	if (CLI.hasOption("IFDS-Explorer")) {
//       		for (int i = 1; i < domain.getSize(); i++) {        			
//                   MyLogger.log(DEBUG,"DomainElement #"+i+" = " + domain.getMappedObject(i));        			
//       		}
//       		GraphUtil.exploreIFDS(flowResult);
//       	}
           return flowResult;
       } catch (CancelException e) {
           throw new CancelRuntimeException(e);
       }
   }
   
   static<E extends ISSABasicBlock> void checkResults(IFDSTaintDomain<E> domain,
		   TabulationResult<BasicBlockInContext<E>,CGNode, DomainElement> flowResult, Set<DomainElement> initialTaints,
		   ISupergraph<BasicBlockInContext<E>, CGNode> graph, BasicBlockInContext<E> methEntryBlock) {
	   System.out.println("***************");
	   System.out.println("Method Analysis");
	   System.out.println(methEntryBlock.getMethod().getSignature());
	   System.out.println("***************");
	   BasicBlockInContext<E> exitBlocks[] = graph.getExitsForProcedure(methEntryBlock.getNode());
	   for (BasicBlockInContext<E> exitBlock:exitBlocks) {
		   IntSet exitResults = flowResult.getResult(exitBlock);
		   for (IntIterator intI = exitResults.intIterator(); intI.hasNext();) {
			   int i = intI.next();
			   DomainElement de = domain.getMappedObject(i);
			   if (initialTaints.contains(de))
				   continue;
			   assert (de!=null);
			   if (de.codeElement instanceof FieldElement) {
				   System.out.println(de.taintSource +" flows into field " + de.codeElement);				   
			   }
			   else if (de.codeElement instanceof ReturnElement) {
				   System.out.println(de.taintSource + " flows into return element " + de.codeElement);				   
			   }
		   }		   
	   }
   }
   

}
