/*
 *
 * Copyright (c) 2010-2012,
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

import spec.SinkSpec;
import spec.Specs;
import util.AndroidAppLoader;
import util.WalaGraphToJGraphT;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.dataflow.IFDS.TabulationResult;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.LocalPointerKey;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.util.intset.IntSet;

import domain.DomainElement;
import domain.IFDSTaintDomain;
import domain.InstanceKeyElement;
import domain.LocalElement;
import flow.types.FlowType;
import flow.types.sinks.EntryArgSinkFlow;

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
            IFDSTaintDomain<E> domain, AndroidAppLoader<E> loader, 
            Map<FlowType, Set<FlowType>> flowGraph, ArrayList<SinkSpec> ssAL) {
    	Collection<IMethod> targets = new HashSet<IMethod>();
    	ArrayList<Collection<IMethod>> targetList = new ArrayList<Collection<IMethod>>();
    	
    	for (int i = 0; i < ssAL.size(); i++) {
    		Collection<IMethod> tempList = ssAL.get(i).getNamePattern().getPossibleTargets(loader.cha);
    		targets.addAll(tempList);
    		targetList.add(tempList);
    	}
    	
        // look for all uses of query function and taint the results with the Uri used in those functions
        Iterator<BasicBlockInContext<E>> graphIt = loader.graph.iterator();
        while (graphIt.hasNext()) {
            BasicBlockInContext<E> block = graphIt.next();
            Iterator<SSAInstruction> instructions = block.iterator();

            while (instructions.hasNext()) {
                SSAInstruction inst = instructions.next();

                if (!(inst instanceof SSAInvokeInstruction)) {
                    continue;
                }
                SSAInvokeInstruction invInst = (SSAInvokeInstruction) inst;
                for(IMethod target:loader.cha.getPossibleTargets(invInst.getDeclaredTarget()))
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

                	                for(InstanceKey ik: loader.pa.getPointsToSet(new LocalPointerKey(node,invInst.getUse(argNums[j])))) {
                	                    for(DomainElement de:domain.getPossibleElements(new InstanceKeyElement(ik))) {
                	                        if(resultSet.contains(domain.getMappedIndex(de))) {
                	                            taintTypeSet.add(de.taintSource);
                	                        }
                	                    }
                	                }
                	                
                    	            for(FlowType dest: ssAL.get(i).getFlowType(loader, invInst, node, argNums[j])) {
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
    		IFDSTaintDomain<E> domain, AndroidAppLoader<E> loader, 
    		Map<FlowType, Set<FlowType>> flowGraph, SinkSpec ss) {

    	int[] newArgNums;    	
    	for (IMethod im:ss.getNamePattern().getPossibleTargets(loader.cha)) {
    		// look for a tainted reply

    		CGNode node = loader.cg.getNode(im, Everywhere.EVERYWHERE);
    		newArgNums = (ss.getArgNums() == null) ? SinkSpec.getNewArgNums((im.isStatic())?im.getNumberOfParameters():im.getNumberOfParameters()-1) : ss.getArgNums();
    		if (node == null || !loader.partialGraph.containsNode(node))
    			continue;

    		for (int i = 0; i < newArgNums.length; i++) {
    			
    			for(DomainElement de:domain.getPossibleElements(new LocalElement(node.getIR().getParameter(newArgNums[i])))) {
    				for (BasicBlockInContext<E> block: loader.graph.getExitsForProcedure(node) ) {
    					if(flowResult.getResult(block).contains(domain.getMappedIndex(de))) {
    						addEdge(flowGraph,de.taintSource,
    								new EntryArgSinkFlow(node, newArgNums[i]));
    					}
    				}
    			}
    			for(InstanceKey ik:loader.pa.getPointsToSet(new LocalPointerKey(node,node.getIR().getParameter(newArgNums[i])))) {
    				for(DomainElement de:domain.getPossibleElements(new InstanceKeyElement(ik))) {
    					for (BasicBlockInContext<E> block : loader.graph.getExitsForProcedure(node)) {
    						if(flowResult.getResult(block).contains(domain.getMappedIndex(de))) {
    							addEdge(flowGraph,de.taintSource,
    									new EntryArgSinkFlow(node, newArgNums[i]));
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
            IFDSTaintDomain<E> domain)
    {
        System.out.println("****************************");
        System.out.println("* Running outflow analysis *");
        System.out.println("****************************");

        Map<FlowType, Set<FlowType>> taintFlow = new HashMap<FlowType,Set<FlowType>>();

        Specs s = new Specs();
        SinkSpec[] ss = s.getSinkSpecs();
        ArrayList<SinkSpec> ssAL = new ArrayList<SinkSpec>();
        for (int i = 0; i < ss.length; i++) {
        	switch (ss[i].getType()) {
        	case INPUT_SINK:
        		processEntryArgs(flowResult, domain, loader, taintFlow, ss[i]);
        		break;
        	case PROVIDER_SINK:
        	case ACTIVITY_SINK:
        	case CALL_SINK:
        	case RETURN_SINK:
        	case SERVICE_SINK:
        		ssAL.add(ss[i]);
        		break;
        	default:
        		throw new UnsupportedOperationException("SourceType not yet Implemented");        			

        	}

        }
        if (!ssAL.isEmpty())
        	processArgSinks(flowResult, domain, loader, taintFlow, ssAL);

        System.out.println("************");
        System.out.println("* Results: *");
        System.out.println("************");

        for(Entry<FlowType,Set<FlowType>> e: taintFlow.entrySet())
        {
            WalaGraphToJGraphT walaJgraphT = new WalaGraphToJGraphT(loader, flowResult, domain, e.getKey());
        	System.out.println("Source: " + e.getKey());
            for(FlowType target:e.getValue())
            {
            	System.out.println("Sink: " + target);
                walaJgraphT.calcPath(e.getKey().getRelevantNode(), target.getRelevantNode());
                System.out.println("Method Trace");
                int counter = 1;
                for (Iterator<DefaultEdge> edgeI = walaJgraphT.getPath().getEdgeList().iterator(); edgeI.hasNext(); counter++) {
                    DefaultEdge edge = edgeI.next();
                    System.out.println("\t#"+counter+": " + walaJgraphT.getJGraphT().getEdgeSource(edge).getMethod().getSignature()
                            + " ==> " + walaJgraphT.getJGraphT().getEdgeTarget(edge).getMethod().getSignature());
                }

            }
        }

        return taintFlow;
    }

}
