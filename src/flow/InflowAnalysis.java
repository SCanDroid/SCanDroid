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


//import static util.MyLogger.LogLevel.DEBUG;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import spec.*;
import util.AndroidAppLoader;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;

import domain.CodeElement;
import flow.types.FlowType;

public class InflowAnalysis <E extends ISSABasicBlock> {

    public static <E extends ISSABasicBlock> void addDomainElements(Map<BasicBlockInContext<E>,Map<FlowType,Set<CodeElement>>> taintMap, BasicBlockInContext<E> block, FlowType taintType, Set<CodeElement> newElements)
    {
        Map<FlowType,Set<CodeElement>> blockMap = taintMap.get(block);
        if(blockMap == null)
        {
            blockMap = new HashMap<FlowType,Set<CodeElement>>();
            taintMap.put(block, blockMap);
        }
        Set<CodeElement> elements = blockMap.get(taintType);
        if(elements == null)
        {
            elements = new HashSet<CodeElement>();
            blockMap.put(taintType, elements);
        }
        elements.addAll(newElements);
    }

    public static <E extends ISSABasicBlock> void addDomainElement(Map<BasicBlockInContext<E>,Map<FlowType,Set<CodeElement>>> taintMap, BasicBlockInContext<E> block, FlowType taintType, CodeElement element)
    {
        Set<CodeElement> elements = new HashSet<CodeElement>();
        elements.add(element);
        addDomainElements(taintMap, block, taintType, elements);
    }

    
    private static<E extends ISSABasicBlock> void processInputSource(Map<BasicBlockInContext<E>,Map<FlowType,Set<CodeElement>>> taintMap, AndroidAppLoader<E> loader, SourceSpec ss) {
    	int[] newArgNums;    	
    	for (IMethod im:ss.getNamePattern().getPossibleTargets(loader.cha)) {
    		CGNode node = loader.cg.getNode(im, Everywhere.EVERYWHERE);
    		newArgNums = (ss.getArgNums() == null) ? SourceSpec.getNewArgNums((im.isStatic())?im.getNumberOfParameters():im.getNumberOfParameters()-1) : ss.getArgNums();
    		if (node == null || !loader.partialGraph.containsNode(node))
    			continue;
    	    		
    		ss.addDomainElements(taintMap, im, null, null, loader, newArgNums);    		

    	}
    }
    
    
    private static<E extends ISSABasicBlock> void processReturnBinderSource(Map<BasicBlockInContext<E>,Map<FlowType,Set<CodeElement>>> taintMap, AndroidAppLoader<E> loader, ArrayList<SourceSpec> ssAL) {
    	Collection<IMethod> targets = new HashSet<IMethod>();
    	ArrayList<Collection<IMethod>> targetList = new ArrayList<Collection<IMethod>>();
    	
    	for (int i = 0; i < ssAL.size(); i++) {
    		Collection<IMethod> tempList = ssAL.get(i).getNamePattern().getPossibleTargets(loader.cha);
    		targets.addAll(tempList);
    		targetList.add(tempList);
    	}
    	
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
				for (IMethod target : loader.cha.getPossibleTargets(invInst.getDeclaredTarget())) {
					if (targets.contains(target)) {
						for (int i = 0; i < targetList.size(); i++) {
							if (targetList.get(i).contains(target)) {

								int[] argNums = ssAL.get(i).getArgNums();
								argNums = (argNums == null) ? SourceSpec.getNewArgNums((target.isStatic())?target.getNumberOfParameters():target.getNumberOfParameters()-1) : argNums;

								ssAL.get(i).addDomainElements(taintMap, null, block, invInst, loader, argNums);

							}
						}									
					}
				}
			}
		}
    }

    public static <E extends ISSABasicBlock>
      Map<BasicBlockInContext<E>,Map<FlowType,Set<CodeElement>>> analyze(
            AndroidAppLoader<E> loader) {

        System.out.println("***************************");
        System.out.println("* Running inflow analysis *");
        System.out.println("***************************");

        Map<BasicBlockInContext<E>,Map<FlowType,Set<CodeElement>>> taintMap =
            new HashMap<BasicBlockInContext<E>,Map<FlowType,Set<CodeElement>>>();
        
        Specs s = new Specs();
        SourceSpec[] ss = s.getSourceSpecs();
        ArrayList<SourceSpec> ssAL = new ArrayList<SourceSpec>();
        for (int i = 0; i < ss.length; i++) {
        	switch(ss[i].getType()){

        	case INPUT_SOURCE:
        		processInputSource(taintMap, loader, ss[i]);
        		break;
        	case PROVIDER_SOURCE:
        		break;
        	case RETURN_SOURCE:
        	case ARG_SOURCE:
        		ssAL.add(ss[i]);
        		break;
        	case BINDER_SOURCE:
        		if (ss[i] instanceof EntryArgSourceSpec)
            		processInputSource(taintMap, loader, ss[i]);
        		else if (ss[i] instanceof CallArgSourceSpec)
        			ssAL.add(ss[i]);
        		break;
        	default:
        		throw new UnsupportedOperationException("SourceType not yet Implemented");        			
        	}
        } 
        if (!ssAL.isEmpty())
        	processReturnBinderSource(taintMap, loader, ssAL);        	   

        System.out.println("************");
        System.out.println("* Results: *");
        System.out.println("************");
        for(Entry<BasicBlockInContext<E>, Map<FlowType,Set<CodeElement>>> e:taintMap.entrySet())
        {
            for(Entry<FlowType,Set<CodeElement>> e2:e.getValue().entrySet())
            {
                for(CodeElement o:e2.getValue())
                {
                    System.out.println("\tBasicBlockInContext: "+e.getKey()+"\n\tFlowType: "+e2.getKey()+"\n\tCodeElement: "+o+"\n");
                }
            }
        }

        return taintMap;
    }

}
