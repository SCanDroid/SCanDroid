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

package org.scandroid.flow;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.scandroid.domain.CodeElement;
import org.scandroid.domain.DomainElement;
import org.scandroid.domain.IFDSTaintDomain;
import org.scandroid.domain.InstanceKeyElement;
import org.scandroid.domain.LocalElement;
import org.scandroid.domain.ReturnElement;
import org.scandroid.flow.types.FlowType;
import org.scandroid.flow.types.ParameterFlow;
import org.scandroid.flow.types.ReturnFlow;
import org.scandroid.spec.CallArgSinkSpec;
import org.scandroid.spec.EntryArgSinkSpec;
import org.scandroid.spec.EntryRetSinkSpec;
import org.scandroid.spec.ISpecs;
import org.scandroid.spec.SinkSpec;
import org.scandroid.util.CGAnalysisContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.dataflow.IFDS.ICFGSupergraph;
import com.ibm.wala.dataflow.IFDS.ISupergraph;
import com.ibm.wala.dataflow.IFDS.TabulationResult;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.LocalPointerKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.OrdinalSet;

public class OutflowAnalysis {
	private static final Logger logger = LoggerFactory
			.getLogger(OutflowAnalysis.class);

	private final CGAnalysisContext<IExplodedBasicBlock> ctx;
	private final CallGraph cg;
	private final ClassHierarchy cha;
	private final PointerAnalysis pa;
	private final ICFGSupergraph graph;
	private final ISpecs specs;

	public OutflowAnalysis(CGAnalysisContext<IExplodedBasicBlock> ctx,
			ISpecs specs) {
		this.ctx = ctx;
		this.cg = ctx.cg;
		this.cha = ctx.getClassHierarchy();
		this.pa = ctx.pa;
		this.graph = (ICFGSupergraph) ctx.graph;
		this.specs = specs;
	}

	private void addEdge(
			Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>> graph,
			FlowType<IExplodedBasicBlock> source,
			FlowType<IExplodedBasicBlock> dest) {
		Set<FlowType<IExplodedBasicBlock>> dests = graph.get(source);
		if (dests == null) {
			dests = new HashSet<FlowType<IExplodedBasicBlock>>();
			graph.put(source, dests);
		}
		dests.add(dest);
	}

	private void processArgSinks(
			TabulationResult<BasicBlockInContext<IExplodedBasicBlock>, CGNode, DomainElement> flowResult,
			IFDSTaintDomain<IExplodedBasicBlock> domain,
			Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>> flowGraph,
			List<SinkSpec> sinkSpecs) {
		List<Collection<IMethod>> targetList = Lists.newArrayList();

		for (int i = 0; i < sinkSpecs.size(); i++) {
			Collection<IMethod> tempList = sinkSpecs.get(i).getNamePattern()
					.getPossibleTargets(cha);
			targetList.add(tempList);
		}

		// look for all uses of query function and taint the results with the
		// Uri used in those functions
		Iterator<BasicBlockInContext<IExplodedBasicBlock>> graphIt = graph
				.iterator();
		while (graphIt.hasNext()) {
			BasicBlockInContext<IExplodedBasicBlock> block = graphIt.next();

			Iterator<SSAInvokeInstruction> invokeInstrs = Iterators.filter(
					block.iterator(), SSAInvokeInstruction.class);

			while (invokeInstrs.hasNext()) {
				SSAInvokeInstruction invInst = invokeInstrs.next();

				for (IMethod target : cha.getPossibleTargets(invInst
						.getDeclaredTarget())) {

					for (int i = 0; i < targetList.size(); i++) {
						if (!targetList.get(i).contains(target)) {
							continue;
						}
						logger.debug("Found target: " + target);
						int[] argNums = sinkSpecs.get(i).getArgNums();

						if (null == argNums) {
							int staticIndex = 0;
							if (target.isStatic()) {
								staticIndex = 1;
							}

							int targetParamCount = target
									.getNumberOfParameters() - staticIndex;
							argNums = SinkSpec.getNewArgNums(targetParamCount);
						}

						CGNode node = block.getNode();

						IntSet resultSet = flowResult.getResult(block);
						for (int j = 0; j < argNums.length; j++) {
							logger.debug("Looping over arg[" + j + "] of "
									+ argNums.length);

							// The set of flow types we're looking for:
							Set<FlowType<IExplodedBasicBlock>> taintTypeSet = Sets
									.newHashSet();

							LocalElement le = new LocalElement(
									invInst.getUse(argNums[j]));
							Set<DomainElement> elements = domain
									.getPossibleElements(le);
							if (elements != null) {
								for (DomainElement de : elements) {
									if (resultSet.contains(domain
											.getMappedIndex(de))) {
										logger.debug("added to taintTypeSpecs: "
												+ de.taintSource);
										taintTypeSet.add(de.taintSource);
									}
								}
							}

							LocalPointerKey lpkey = new LocalPointerKey(node,
									invInst.getUse(argNums[j]));
							for (InstanceKey ik : pa.getPointsToSet(lpkey)) {
								for (DomainElement de : domain
										.getPossibleElements(new InstanceKeyElement(
												ik))) {
									if (resultSet.contains(domain
											.getMappedIndex(de))) {
										logger.debug("added to taintTypeSpecs: "
												+ de.taintSource);
										taintTypeSet.add(de.taintSource);
									}
								}
							}

							for (FlowType<IExplodedBasicBlock> dest : sinkSpecs
									.get(i).getFlowType(block)) {
								for (FlowType<IExplodedBasicBlock> source : taintTypeSet) {
									logger.debug("added edge: " + source
											+ " \n \tto \n\t" + dest);
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
	
	private void processEntryArgs(TabulationResult<BasicBlockInContext<IExplodedBasicBlock>, CGNode, DomainElement> flowResult,
			IFDSTaintDomain<IExplodedBasicBlock> domain,
			Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>> flowGraph,
			SinkSpec ss) {
		Set<SinkPoint> sinkPoints = calculateSinkPoints((EntryArgSinkSpec) ss);
		
		for (SinkPoint sinkPoint : sinkPoints) {
			for (FlowType<IExplodedBasicBlock> source : findSources(flowResult, domain, sinkPoint)) {
				addEdge(flowGraph, source, sinkPoint.sinkFlow);
			}
		}
	}

	private void processEntryArgs_old(
			TabulationResult<BasicBlockInContext<IExplodedBasicBlock>, CGNode, DomainElement> flowResult,
			IFDSTaintDomain<IExplodedBasicBlock> domain,
			Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>> flowGraph,
			SinkSpec ss) {

		int[] newArgNums;
		for (IMethod im : ss.getNamePattern().getPossibleTargets(cha)) {
			// look for a tainted reply

			CGNode node = cg.getNode(im, Everywhere.EVERYWHERE);
			if (node == null) {
				logger.warn("null CGNode for {}", im.getSignature());
				continue;
			}

			BasicBlockInContext<IExplodedBasicBlock>[] entriesForProcedure = graph
					.getEntriesForProcedure(node);
			if (entriesForProcedure == null || 0 == entriesForProcedure.length) {
				logger.warn("procedure without entries {}", im.getSignature());
				continue;
			}
			if (1 != entriesForProcedure.length) {
				logger.error("More than one procedure entry.  (Are you sure you're using an ICFGSupergraph?)");
			}
			BasicBlockInContext<IExplodedBasicBlock> entryBlock = entriesForProcedure[0];

			newArgNums = ss.getArgNums();
			if (null == newArgNums) {
				int staticIndex = 1;
				if (im.isStatic()) {
					staticIndex = 0;
				}
				int targetParamCount = im.getNumberOfParameters() - staticIndex;

				newArgNums = SinkSpec.getNewArgNums(targetParamCount);
			}
			// for (BasicBlockInContext<E> block:
			// graph.getExitsForProcedure(node) ) {
			// IntIterator itr = flowResult.getResult(block).intIterator();
			// while (itr.hasNext()) {
			// int i = itr.next();
			// logger.debug("domain element at exit: "+domain.getMappedObject(i));
			//
			//
			// }
			// }
			for (int i = 0; i < newArgNums.length; i++) {

				// see if anything flowed into the args as sinks:
				for (DomainElement de : domain
						.getPossibleElements(new LocalElement(node.getIR()
								.getParameter(newArgNums[i])))) {

					for (BasicBlockInContext<IExplodedBasicBlock> block : graph
							.getExitsForProcedure(node)) {

						int mappedIndex = domain.getMappedIndex(de);
						if (flowResult.getResult(block).contains(mappedIndex)) {
							addEdge(flowGraph, de.taintSource,
									new ParameterFlow<IExplodedBasicBlock>(
											entryBlock, newArgNums[i], false));
						}
					}

					int mappedIndex = domain.getMappedIndex(de);
					if (flowResult.getResult(entryBlock).contains(mappedIndex)) {
						addEdge(flowGraph, de.taintSource,
								new ParameterFlow<IExplodedBasicBlock>(
										entryBlock, newArgNums[i], false));
					}

				}
				for (InstanceKey ik : pa.getPointsToSet(new LocalPointerKey(
						node, node.getIR().getParameter(newArgNums[i])))) {
					for (DomainElement de : domain
							.getPossibleElements(new InstanceKeyElement(ik))) {
						if (flowResult.getResult(entryBlock).contains(
								domain.getMappedIndex(de))) {
							logger.trace("found outflow in second EntryArgSink loop");
							addEdge(flowGraph, de.taintSource,
									new ParameterFlow<IExplodedBasicBlock>(
											entryBlock, newArgNums[i], false));
						}
					}
				}
			}
		}
	}

	private void processEntryRets(
			TabulationResult<BasicBlockInContext<IExplodedBasicBlock>, CGNode, DomainElement> flowResult,
			IFDSTaintDomain<IExplodedBasicBlock> domain,
			Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>> flowGraph,
			SinkSpec ss) {

		for (IMethod im : ss.getNamePattern().getPossibleTargets(cha)) {
			// look for a tainted reply

			CGNode node = cg.getNode(im, Everywhere.EVERYWHERE);

			if (node == null) {
				logger.warn("could not find CGNode for SinkSpec {}", ss);
				continue;
			}

			BasicBlockInContext<IExplodedBasicBlock>[] exitsForProcedure = graph
					.getExitsForProcedure(node);
			if (exitsForProcedure == null || 0 == exitsForProcedure.length) {
				logger.warn("could not find exit blocks for SinkSpec {}", ss);
				continue;
			}

			final Set<DomainElement> possibleElements = domain
					.getPossibleElements(new ReturnElement());
			logger.debug("{} possible elements found for ReturnElement",
					possibleElements.size());
			for (DomainElement de : possibleElements) {
				logger.debug("processing domain element {}", de);
				for (BasicBlockInContext<IExplodedBasicBlock> block : exitsForProcedure) {
					logger.debug("{} instructions in block",
							block.getLastInstructionIndex());
					if (flowResult.getResult(block).contains(
							domain.getMappedIndex(de))) {
						logger.debug("original block has edge");
						addEdge(flowGraph, de.taintSource,
								new ReturnFlow<IExplodedBasicBlock>(block,
										false));
					}
					// Iterator<BasicBlockInContext<E>> it =
					// graph.getPredNodes(block);
					// while (it.hasNext()) {
					// BasicBlockInContext<E> realBlock = it.next();
					// if (realBlock.isExitBlock()) {
					// logger.warn("found edge to exit");
					// // addEdge(flowGraph,de.taintSource, new
					// ReturnFlow<E>(realBlock, false));
					// }
					// if(flowResult.getResult(realBlock).contains(domain.getMappedIndex(de)))
					// {
					// logger.debug("adding edge from {} to ReturnFlow",
					// de.taintSource);
					// addEdge(flowGraph,de.taintSource, new
					// ReturnFlow<E>(realBlock, false));
					// } else {
					// logger.debug("no edge from block {} for {}", realBlock,
					// de);
					// }
				}
			}

			for (BasicBlockInContext<IExplodedBasicBlock> block : exitsForProcedure) {
				Iterator<BasicBlockInContext<IExplodedBasicBlock>> it = graph
						.getPredNodes(block);
				while (it.hasNext()) {
					BasicBlockInContext<IExplodedBasicBlock> realBlock = it
							.next();
					final SSAInstruction inst = realBlock.getLastInstruction();
					if (null != inst && inst instanceof SSAReturnInstruction) {
						PointerKey pk = new LocalPointerKey(node,
								inst.getUse(0));
						for (InstanceKey ik : pa.getPointsToSet(pk)) {
							for (DomainElement ikElement : domain
									.getPossibleElements(new InstanceKeyElement(
											ik))) {
								if (flowResult.getResult(realBlock).contains(
										domain.getMappedIndex(ikElement))) {
									addEdge(flowGraph,
											ikElement.taintSource,
											new ReturnFlow<IExplodedBasicBlock>(
													realBlock, false));
								}
							}
						}
					}
				}
			}
		}
	}

	public Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>> analyze(
			TabulationResult<BasicBlockInContext<IExplodedBasicBlock>, CGNode, DomainElement> flowResult,
			IFDSTaintDomain<IExplodedBasicBlock> domain) {
		return analyze(ctx.cg, ctx.getClassHierarchy(), ctx.graph, ctx.pa,
				flowResult, domain, specs);
	}

	public Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>> analyze(
			CallGraph cg,
			ClassHierarchy cha,
			ISupergraph<BasicBlockInContext<IExplodedBasicBlock>, CGNode> graph,
			PointerAnalysis pa,
			TabulationResult<BasicBlockInContext<IExplodedBasicBlock>, CGNode, DomainElement> flowResult,
			IFDSTaintDomain<IExplodedBasicBlock> domain, ISpecs s) {

		logger.debug("****************************");
		logger.debug("* Running outflow analysis *");
		logger.debug("****************************");

		Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>> taintFlow = Maps
				.newHashMap();

		SinkSpec[] ss = s.getSinkSpecs();
		logger.debug(ss.length + " sink Specs. ");

		List<SinkSpec> ssAL = Lists.newArrayList();
		for (int i = 0; i < ss.length; i++) {
			if (ss[i] instanceof EntryArgSinkSpec)
				processEntryArgs(flowResult, domain, taintFlow, ss[i]);
			else if (ss[i] instanceof CallArgSinkSpec)
				ssAL.add(ss[i]);
			else if (ss[i] instanceof EntryRetSinkSpec)
				processEntryRets(flowResult, domain, taintFlow, ss[i]);
			else
				throw new UnsupportedOperationException(
						"SinkSpec not yet Implemented");
		}
		if (!ssAL.isEmpty())
			processArgSinks(flowResult, domain, taintFlow, ssAL);

		logger.info("************");
		logger.info("* Results: *");
		logger.info("************");

		logger.debug("{}", taintFlow.toString());

		/* TODO: re-enable this soon! */
		/*
		 * for(Entry<FlowType,Set<FlowType>> e: taintFlow.entrySet()) {
		 * WalaGraphToJGraphT walaJgraphT = new WalaGraphToJGraphT(flowResult,
		 * domain, e.getKey(), graph, cg); logger.debug("Source: " +
		 * e.getKey()); for(FlowType target:e.getValue()) {
		 * logger.debug("\t=> Sink: " + target); //logger.debug("SourceNode: "+
		 * e.getKey().getRelevantNode() +
		 * "\nSinkNode: "+target.getRelevantNode());
		 * walaJgraphT.calcPath(e.getKey().getRelevantNode(),
		 * target.getRelevantNode()); Iterator<DefaultEdge> edgeI =
		 * walaJgraphT.getPath().getEdgeList().iterator(); if (edgeI.hasNext())
		 * logger.debug("\t::Method Trace::"); int counter = 1; while
		 * (edgeI.hasNext()) { DefaultEdge edge = edgeI.next();
		 * logger.debug("\t\t#"+counter+": " +
		 * walaJgraphT.getJGraphT().getEdgeSource
		 * (edge).getMethod().getSignature() + " ==> " +
		 * walaJgraphT.getJGraphT()
		 * .getEdgeTarget(edge).getMethod().getSignature()); }
		 * 
		 * } }
		 */

		return taintFlow;
	}

	private Set<SinkPoint> calculateSinkPoints(EntryArgSinkSpec sinkSpec) {
		Set<SinkPoint> points = Sets.newHashSet();

		Collection<IMethod> methods = sinkSpec.getNamePattern()
				.getPossibleTargets(cha);
		if (null == methods) {
			logger.warn("no methods found for sink spec {}", sinkSpec);
		}

		for (IMethod method : methods) {
			CGNode node = cg.getNode(method, Everywhere.EVERYWHERE);
			BasicBlockInContext<IExplodedBasicBlock> entryBlock = graph
					.getICFG().getEntry(node);
			BasicBlockInContext<IExplodedBasicBlock> exitBlock = graph
					.getICFG().getExit(node);
			for (int argNum : sinkSpec.getArgNums()) {
				final int ssaVal = node.getIR().getParameter(argNum);
				final ParameterFlow<IExplodedBasicBlock> sinkFlow = new ParameterFlow<IExplodedBasicBlock>(
						entryBlock, argNum, false);
				final SinkPoint sinkPoint = new SinkPoint(exitBlock, ssaVal,
						sinkFlow);
				points.add(sinkPoint);
			}
		}
		return points;
	}

	private Set<FlowType<IExplodedBasicBlock>> findSources(
			TabulationResult<BasicBlockInContext<IExplodedBasicBlock>, CGNode, DomainElement> flowResult,
			IFDSTaintDomain<IExplodedBasicBlock> domain, SinkPoint sinkPoint) {
		Set<FlowType<IExplodedBasicBlock>> sources = Sets.newHashSet();
		
		final CodeElement localElt = new LocalElement(
				sinkPoint.ssaVal);
		Set<CodeElement> elts = Sets.newHashSet(localElt);

		final CGNode node = sinkPoint.block.getNode();
		PointerKey pk = pa.getHeapModel().getPointerKeyForLocal(node,
				sinkPoint.ssaVal);
		OrdinalSet<InstanceKey> iks = pa.getPointsToSet(pk);
		if (null == iks) {
			logger.warn("no instance keys found for SinkPoint {}", sinkPoint);
		}

		for (InstanceKey ik : iks) {
			InstanceKeyElement elt = new InstanceKeyElement(ik);
			elts.add(elt);
		}
		
		for (CodeElement elt : elts) {
			for (DomainElement de : domain.getPossibleElements(elt)) {
				if (flowResult.getResult(sinkPoint.block).contains(domain.getMappedIndex(de))) {
					sources.add(de.taintSource);
				}
			}			
		}

		return sources;
	}

	private static class SinkPoint {
		private final BasicBlockInContext<IExplodedBasicBlock> block;
		private final int ssaVal;
		private final FlowType<IExplodedBasicBlock> sinkFlow;

		public SinkPoint(BasicBlockInContext<IExplodedBasicBlock> block,
				int ssaVal, FlowType<IExplodedBasicBlock> sinkFlow) {
			this.block = block;
			this.ssaVal = ssaVal;
			this.sinkFlow = sinkFlow;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((block == null) ? 0 : block.hashCode());
			result = prime * result
					+ ((sinkFlow == null) ? 0 : sinkFlow.hashCode());
			result = prime * result + ssaVal;
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			SinkPoint other = (SinkPoint) obj;
			if (block == null) {
				if (other.block != null)
					return false;
			} else if (!block.equals(other.block))
				return false;
			if (sinkFlow == null) {
				if (other.sinkFlow != null)
					return false;
			} else if (!sinkFlow.equals(other.sinkFlow))
				return false;
			if (ssaVal != other.ssaVal)
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "SinkPoint [block=" + block + ", ssaVal=" + ssaVal
					+ ", sinkFlow=" + sinkFlow + "]";
		}

	}

}
