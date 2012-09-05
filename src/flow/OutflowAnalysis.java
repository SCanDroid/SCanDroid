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

package flow;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.jgrapht.graph.DefaultEdge;

import spec.CallArgSinkSpec;
import spec.EntryArgSinkSpec;
import spec.SinkSpec;
import spec.ISpecs;
import util.AndroidAppLoader;
import util.WalaGraphToJGraphT;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.dataflow.IFDS.ISupergraph;
import com.ibm.wala.dataflow.IFDS.TabulationResult;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.LocalPointerKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.util.intset.IntSet;

import domain.DomainElement;
import domain.IFDSTaintDomain;
import domain.InstanceKeyElement;
import domain.LocalElement;
import flow.types.FlowType;
import flow.types.ReturnFlow;


public class OutflowAnalysis <E extends ISSABasicBlock> {

    private static void addEdge(
            Map<FlowType,Set<FlowType>> graph, FlowType source, FlowType dest)
    {
        Set<FlowType> dests = graph.get(source);
        if(dests == null)
        {
            dests = new HashSet<FlowType>();
            graph.put(source, dests);
        }
        dests.add(dest);
    }
    
    private static <E extends ISSABasicBlock> void processArgSinks(
            TabulationResult<BasicBlockInContext<E>, CGNode, DomainElement> flowResult,
            IFDSTaintDomain<E> domain, Map<FlowType, Set<FlowType>> flowGraph, 
            ArrayList<SinkSpec> ssAL, ClassHierarchy cha, PointerAnalysis pa, 
            ISupergraph<BasicBlockInContext<E>, CGNode> graph, CallGraph cg) {
    	Collection<IMethod> targets = new HashSet<IMethod>();
    	ArrayList<Collection<IMethod>> targetList = new ArrayList<Collection<IMethod>>();
    	
    	for (int i = 0; i < ssAL.size(); i++) {
    		Collection<IMethod> tempList = ssAL.get(i).getNamePattern().getPossibleTargets(cha);
    		targets.addAll(tempList);
    		targetList.add(tempList);
    	}
    	
        // look for all uses of query function and taint the results with the Uri used in those functions
        Iterator<BasicBlockInContext<E>> graphIt = graph.iterator();
        while (graphIt.hasNext()) {
            BasicBlockInContext<E> block = graphIt.next();
            Iterator<SSAInstruction> instructions = block.iterator();

            while (instructions.hasNext()) {
                SSAInstruction inst = instructions.next();

                if (!(inst instanceof SSAInvokeInstruction)) {
                    continue;
                }
                SSAInvokeInstruction invInst = (SSAInvokeInstruction) inst;
                for(IMethod target:cha.getPossibleTargets(invInst.getDeclaredTarget()))
                {
                	if (targets.contains(target)) {
                		for (int i = 0; i < targetList.size(); i++) {
                			if (targetList.get(i).contains(target)) {
                				int[] argNums = ssAL.get(i).getArgNums();
                				argNums = (argNums == null) ? SinkSpec.getNewArgNums((target.isStatic())?target.getNumberOfParameters():target.getNumberOfParameters()-1) : argNums;
                				
                	            CGNode node = block.getNode();

                	            IntSet resultSet = flowResult.getResult(block);
                	            for(int j = 0; j<argNums.length; j++) {
                    	            Set<FlowType> taintTypeSet = new HashSet<FlowType>();

                	                LocalElement le = new LocalElement(invInst.getUse(argNums[j]));
                	                Set<DomainElement> elements = domain.getPossibleElements(le);
                	                if(elements != null) {
                	                    for(DomainElement de:elements) {
                	                        if(resultSet.contains(domain.getMappedIndex(de))) {
                	                            taintTypeSet.add(de.taintSource);
                	                        }
                	                    }
                	                }

                	                for(InstanceKey ik: pa.getPointsToSet(new LocalPointerKey(node,invInst.getUse(argNums[j])))) {
                	                    for(DomainElement de:domain.getPossibleElements(new InstanceKeyElement(ik))) {
                	                        if(resultSet.contains(domain.getMappedIndex(de))) {
                	                            taintTypeSet.add(de.taintSource);
                	                        }
                	                    }
                	                }
                	                
                    	            for(FlowType dest: ssAL.get(i).getFlowType(target, invInst, node, argNums[j], pa)) {
                    	                for(FlowType source: taintTypeSet) {
                    	                    // flow taint into uriIK
                    	                    addEdge(flowGraph, source, dest);
                    	                }
                    	            }
                	            }
                			}
                		}
                	}
                }
            }
        }
    }
    
    private static <E extends ISSABasicBlock> void processEntryArgs(
    		TabulationResult<BasicBlockInContext<E>, CGNode, DomainElement> flowResult,
    		IFDSTaintDomain<E> domain, Map<FlowType, Set<FlowType>> flowGraph, 
    		SinkSpec ss, CallGraph cg, ISupergraph<BasicBlockInContext<E>, CGNode> graph, PointerAnalysis pa, ClassHierarchy cha) {

    	int[] newArgNums;    	
    	for (IMethod im:ss.getNamePattern().getPossibleTargets(cha)) {
    		// look for a tainted reply

    		CGNode node = cg.getNode(im, Everywhere.EVERYWHERE);
    		if (node == null) {
    		    continue; 
    		}

    		BasicBlockInContext<E>[] entriesForProcedure = graph.getEntriesForProcedure(node);
            if (entriesForProcedure == null || 0 == entriesForProcedure.length) {
    			continue;
    		}
    		
    		newArgNums = (ss.getArgNums() == null) ? SinkSpec.getNewArgNums((im.isStatic())?im.getNumberOfParameters():im.getNumberOfParameters()-1) : ss.getArgNums();

    		for (int i = 0; i < newArgNums.length; i++) {
    			
    			for(DomainElement de:domain.getPossibleElements(new LocalElement(node.getIR().getParameter(newArgNums[i])))) {
    				for (BasicBlockInContext<E> block: graph.getExitsForProcedure(node) ) {
    					if(flowResult.getResult(block).contains(domain.getMappedIndex(de))) {
    						addEdge(flowGraph,de.taintSource, new ReturnFlow(im.getDeclaringClass().getReference(), node, "EntryArgSink", im.getSignature(), newArgNums[i]));
    					}
    				}
    			}
    			for(InstanceKey ik:pa.getPointsToSet(new LocalPointerKey(node,node.getIR().getParameter(newArgNums[i])))) {
    				for(DomainElement de:domain.getPossibleElements(new InstanceKeyElement(ik))) {
    					for (BasicBlockInContext<E> block : graph.getExitsForProcedure(node)) {
    						if(flowResult.getResult(block).contains(domain.getMappedIndex(de))) {
    							addEdge(flowGraph,de.taintSource, new ReturnFlow(im.getDeclaringClass().getReference(), node, "EntryArgSink", im.getSignature(), newArgNums[i]));
    						}
    					}
    				}
    			}
    		}
    	}
    }

    public static <E extends ISSABasicBlock> Map<FlowType, Set<FlowType>>
      analyze(AndroidAppLoader<E> loader,
            TabulationResult<BasicBlockInContext<E>, CGNode, DomainElement> flowResult,
            IFDSTaintDomain<E> domain,
            ISpecs s) {
        return analyze(loader.cg, loader.cha, loader.graph, loader.pa, 
                flowResult, domain, s);
    }
     
     public static <E extends ISSABasicBlock> Map<FlowType, Set<FlowType>>
     analyze(CallGraph cg, 
          ClassHierarchy cha, 
          ISupergraph<BasicBlockInContext<E>, CGNode> graph,
          PointerAnalysis pa,
          TabulationResult<BasicBlockInContext<E>, CGNode, DomainElement> flowResult,
          IFDSTaintDomain<E> domain,
          ISpecs s) {
         
        System.out.println("****************************");
        System.out.println("* Running outflow analysis *");
        System.out.println("****************************");

        Map<FlowType, Set<FlowType>> taintFlow = new HashMap<FlowType,Set<FlowType>>();

        SinkSpec[] ss = s.getSinkSpecs();
        
        ArrayList<SinkSpec> ssAL = new ArrayList<SinkSpec>();
        for (int i = 0; i < ss.length; i++) {
        	if (ss[i] instanceof EntryArgSinkSpec)
        		processEntryArgs(flowResult, domain, taintFlow, ss[i], cg, graph, pa, cha);
        	else if (ss[i] instanceof CallArgSinkSpec)
        		ssAL.add(ss[i]);
        	else
        		throw new UnsupportedOperationException("SourceSpec not yet Implemented");
        }
        if (!ssAL.isEmpty())
        	processArgSinks(flowResult, domain, taintFlow, ssAL, cha, pa, graph, cg);

        System.out.println("************");
        System.out.println("* Results: *");
        System.out.println("************");

        for(Entry<FlowType,Set<FlowType>> e: taintFlow.entrySet())
        {
            WalaGraphToJGraphT walaJgraphT = new WalaGraphToJGraphT(flowResult, domain, e.getKey(), graph, cg);
        	System.out.println("Source: " + e.getKey());
            for(FlowType target:e.getValue())
            {
            	System.out.println("\t=> Sink: " + target);
            	//System.out.println("SourceNode: "+ e.getKey().getRelevantNode() + "\nSinkNode: "+target.getRelevantNode());
                walaJgraphT.calcPath(e.getKey().getRelevantNode(), target.getRelevantNode());
                Iterator<DefaultEdge> edgeI = walaJgraphT.getPath().getEdgeList().iterator();
                if (edgeI.hasNext())
                	System.out.println("\t::Method Trace::");
                int counter = 1;
                while (edgeI.hasNext()) {
                    DefaultEdge edge = edgeI.next();
                    System.out.println("\t\t#"+counter+": " + walaJgraphT.getJGraphT().getEdgeSource(edge).getMethod().getSignature()
                            + " ==> " + walaJgraphT.getJGraphT().getEdgeTarget(edge).getMethod().getSignature());              
                }

            }
        }

        return taintFlow;
    }

}
