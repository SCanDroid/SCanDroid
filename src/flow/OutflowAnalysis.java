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

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spec.CallArgSinkSpec;
import spec.EntryArgSinkSpec;
import spec.EntryRetSinkSpec;
import spec.ISpecs;
import spec.SinkSpec;
import util.AndroidAnalysisContext;

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
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
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.util.intset.IntIterator;
import com.ibm.wala.util.intset.IntSet;

import domain.DomainElement;
import domain.IFDSTaintDomain;
import domain.InstanceKeyElement;
import domain.LocalElement;
import domain.ReturnElement;
import flow.types.FlowType;
import flow.types.ParameterFlow;
import flow.types.ReturnFlow;


public class OutflowAnalysis <E extends ISSABasicBlock> {
	private static final Logger logger = LoggerFactory.getLogger(OutflowAnalysis.class);

    private static <E extends ISSABasicBlock> void addEdge(
            Map<FlowType<E>,Set<FlowType<E>>> graph, FlowType<E> source, FlowType<E> dest)
    {
        Set<FlowType<E>> dests = graph.get(source);
        if(dests == null)
        {
            dests = new HashSet<FlowType<E>>();
            graph.put(source, dests);
        }
        dests.add(dest);
    }
    
    private static <E extends ISSABasicBlock> 
    void processArgSinks(
      TabulationResult<BasicBlockInContext<E>, CGNode, DomainElement> flowResult,
      IFDSTaintDomain<E> domain,
      Map<FlowType<E>, Set<FlowType<E>>> flowGraph, 
      List<SinkSpec> sinkSpecs,
      ClassHierarchy cha,
      PointerAnalysis pa, 
      ISupergraph<BasicBlockInContext<E>, CGNode> graph,
      CallGraph cg) {
    	List<Collection<IMethod>> targetList = Lists.newArrayList();
    	
    	for (int i = 0; i < sinkSpecs.size(); i++) {
    		Collection<IMethod> tempList = sinkSpecs.get(i).getNamePattern().getPossibleTargets(cha);
    		targetList.add(tempList);
    	}
    	
        // look for all uses of query function and taint the results with the Uri used in those functions
        Iterator<BasicBlockInContext<E>> graphIt = graph.iterator();
        while (graphIt.hasNext()) {
            BasicBlockInContext<E> block = graphIt.next();

            Iterator<SSAInvokeInstruction> invokeInstrs =
                Iterators.filter(block.iterator(), SSAInvokeInstruction.class);
            
            while (invokeInstrs.hasNext()) {
                SSAInvokeInstruction invInst = invokeInstrs.next();
                
                for(IMethod target : cha.getPossibleTargets(invInst.getDeclaredTarget())) {

            		for (int i = 0; i < targetList.size(); i++) {
            			if (!targetList.get(i).contains(target)) {
            				continue;
            			}
            			logger.debug("Found target: "+target);
        				int[] argNums = sinkSpecs.get(i).getArgNums();
        				
        				if (null == argNums) {
        					int staticIndex = 0 ;
        					if (target.isStatic()) {
        						staticIndex = 1;
        					}
        					
        					int targetParamCount = target.getNumberOfParameters() - staticIndex;
        					argNums = SinkSpec.getNewArgNums(targetParamCount);
        				}
        				
        	            CGNode node = block.getNode();

        	            IntSet resultSet = flowResult.getResult(block);
        	            for(int j = 0; j < argNums.length; j++) {
        	            	logger.debug("Looping over arg["+j+"] of "+argNums.length);
        	            	
        	            	// The set of flow types we're looking for:
        	            	Set<FlowType<E>> taintTypeSet = Sets.newHashSet();

        	                LocalElement le = new LocalElement(invInst.getUse(argNums[j]));
        	                Set<DomainElement> elements = domain.getPossibleElements(le);
        	                if(elements != null) {
        	                    for(DomainElement de:elements) {
        	                        if(resultSet.contains(domain.getMappedIndex(de))) {
        	                        	logger.debug("added to taintTypeSpecs: "+de.taintSource);
        	                            taintTypeSet.add(de.taintSource);
        	                        }
        	                    }
        	                }

        	                LocalPointerKey lpkey = new LocalPointerKey(node,invInst.getUse(argNums[j]));
							for(InstanceKey ik: pa.getPointsToSet(lpkey)) {
        	                    for(DomainElement de :
        	                    	domain.getPossibleElements(new InstanceKeyElement(ik))) {
        	                        if(resultSet.contains(domain.getMappedIndex(de))) {
        	                        	logger.debug("added to taintTypeSpecs: "+de.taintSource);
        	                            taintTypeSet.add(de.taintSource);
        	                        }
        	                    }
        	                }
        	                
                            for(FlowType<E> dest: sinkSpecs.get(i).getFlowType(block)) {
					            for(FlowType<E> source: taintTypeSet) {
					            	logger.debug("added edge: "+source+" \n \tto \n\t"+dest);
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
    
    private static <E extends ISSABasicBlock> void processEntryArgs(
    		TabulationResult<BasicBlockInContext<E>, CGNode, DomainElement> flowResult,
    		IFDSTaintDomain<E> domain, 
    		Map<FlowType<E>, Set<FlowType<E>>> flowGraph, 
    		SinkSpec ss, 
    		CallGraph cg, 
    		ISupergraph<BasicBlockInContext<E>, CGNode> graph, 
    		PointerAnalysis pa, 
    		ClassHierarchy cha) {

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
            if (1 != entriesForProcedure.length) {
            	logger.error("More than one procedure entry.  (Are you sure you're using an ICFGSupergraph?)");
            }
            BasicBlockInContext<E> entryBlock = entriesForProcedure[0];
            
            newArgNums = ss.getArgNums();
            if (null == newArgNums ) {
            	int staticIndex = 1;
				if (im.isStatic()) {
					staticIndex = 0;
				}
				int targetParamCount = im.getNumberOfParameters() - staticIndex;
				
            	newArgNums = SinkSpec.getNewArgNums( targetParamCount );
            }
//            for (BasicBlockInContext<E> block: graph.getExitsForProcedure(node) ) {
//            	IntIterator itr = flowResult.getResult(block).intIterator();
//            	while (itr.hasNext()) {
//					int i = itr.next();
//					logger.debug("domain element at exit: "+domain.getMappedObject(i));
//					
//					
//				}
//            }
            for (int i = 0; i < newArgNums.length; i++) {
    			
            	// see if anything flowed into the args as sinks:
    			for(DomainElement de:domain.getPossibleElements(new LocalElement(node.getIR().getParameter(newArgNums[i])))) {
    				
    				for (BasicBlockInContext<E> block: graph.getExitsForProcedure(node) ) {

    					int mappedIndex = domain.getMappedIndex(de);
						if(flowResult.getResult(block).contains(mappedIndex)) {
							addEdge(flowGraph, de.taintSource, new ParameterFlow<E>(entryBlock, newArgNums[i], false));
						}
    				}
    				
					int mappedIndex = domain.getMappedIndex(de);
					if(flowResult.getResult(entryBlock).contains(mappedIndex)) {
                        addEdge(flowGraph,de.taintSource, new ParameterFlow<E>(entryBlock, newArgNums[i], false));
					}
    				
    			}
    			for(InstanceKey ik:pa.getPointsToSet(new LocalPointerKey(node,node.getIR().getParameter(newArgNums[i])))) {
    				for(DomainElement de:domain.getPossibleElements(new InstanceKeyElement(ik))) {
						if(flowResult.getResult(entryBlock).contains(domain.getMappedIndex(de))) {
							logger.trace("found outflow in second EntryArgSink loop");
                            addEdge(flowGraph,de.taintSource, new ParameterFlow<E>(entryBlock, newArgNums[i], false));
						}
    				}
    			}
    		}
    	}
    }
    
    private static <E extends ISSABasicBlock> void processEntryRets(
    		TabulationResult<BasicBlockInContext<E>, CGNode, DomainElement> flowResult,
    		IFDSTaintDomain<E> domain, 
    		Map<FlowType<E>, Set<FlowType<E>>> flowGraph, 
    		SinkSpec ss, 
    		CallGraph cg, 
    		ISupergraph<BasicBlockInContext<E>, CGNode> graph, 
    		PointerAnalysis pa, 
    		ClassHierarchy cha) {

    	for (IMethod im:ss.getNamePattern().getPossibleTargets(cha)) {
    		// look for a tainted reply

    		CGNode node = cg.getNode(im, Everywhere.EVERYWHERE);
    		if (node == null) {
    		    continue; 
    		}
    		
    		BasicBlockInContext<E>[] exitsForProcedure = graph.getExitsForProcedure(node);
            if (exitsForProcedure == null || 0 == exitsForProcedure.length) {
    			continue;
    		}        
            
            for(DomainElement de:domain.getPossibleElements(new ReturnElement())) {
            	for (BasicBlockInContext<E> block: exitsForProcedure) {
            		Iterator<BasicBlockInContext<E>> it = graph.getPredNodes(block);
        			while (it.hasNext()) {
        				BasicBlockInContext<E> realBlock = it.next();
                		if(flowResult.getResult(realBlock).contains(domain.getMappedIndex(de))) {
                			addEdge(flowGraph,de.taintSource, new ReturnFlow<E>(realBlock, false));
                		}
					}  
            	}
            }
    	}
    }

    public static <E extends ISSABasicBlock> Map<FlowType<E>, Set<FlowType<E>>>
      analyze(AndroidAnalysisContext<E> loader,
            TabulationResult<BasicBlockInContext<E>, CGNode, DomainElement> flowResult,
            IFDSTaintDomain<E> domain,
            ISpecs s) {
        return analyze(loader.cg, loader.cha, loader.graph, loader.pa, 
                flowResult, domain, s);
    }
     
     public static <E extends ISSABasicBlock> Map<FlowType<E>, Set<FlowType<E>>>
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

        Map<FlowType<E>, Set<FlowType<E>>> taintFlow = Maps.newHashMap();

        SinkSpec[] ss = s.getSinkSpecs();
        logger.debug(ss.length + " sink Specs. ");
        
        List<SinkSpec> ssAL = Lists.newArrayList();
        for (int i = 0; i < ss.length; i++) {
        	if (ss[i] instanceof EntryArgSinkSpec)
        		processEntryArgs(flowResult, domain, taintFlow, ss[i], cg, graph, pa, cha);
        	else if (ss[i] instanceof CallArgSinkSpec)
        		ssAL.add(ss[i]);
        	else if (ss[i] instanceof EntryRetSinkSpec)
        		processEntryRets(flowResult, domain, taintFlow, ss[i], cg, graph, pa, cha);
        	else	
        		throw new UnsupportedOperationException("SinkSpec not yet Implemented");
        }
        if (!ssAL.isEmpty())
        	processArgSinks(flowResult, domain, taintFlow, ssAL, cha, pa, graph, cg);

        System.out.println("************");
        System.out.println("* Results: *");
        System.out.println("************");

        /* TODO: re-enable this soon! */
        /*
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
        */

        return taintFlow;
    }

}
