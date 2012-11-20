package org.scandroid.flow;

import java.util.Set;

import org.scandroid.domain.CodeElement;
import org.scandroid.domain.DomainElement;
import org.scandroid.domain.IFDSTaintDomain;
import org.scandroid.domain.LocalElement;
import org.scandroid.flow.types.FlowType;
import org.scandroid.util.CGAnalysisContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;
import com.ibm.wala.dataflow.IFDS.TabulationResult;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.util.intset.OrdinalSet;

public class LocalSinkPoint implements ISinkPoint {
	private static final Logger logger = LoggerFactory.getLogger(LocalSinkPoint.class);
	
	private final BasicBlockInContext<IExplodedBasicBlock> block;
	private final int ssaVal;
	private final FlowType<IExplodedBasicBlock> sinkFlow;

	public LocalSinkPoint(BasicBlockInContext<IExplodedBasicBlock> block,
			int ssaVal, FlowType<IExplodedBasicBlock> sinkFlow) {
		this.block = block;
		this.ssaVal = ssaVal;
		this.sinkFlow = sinkFlow;
	}
	
	@Override
	public Set<FlowType<IExplodedBasicBlock>> findSources(CGAnalysisContext<IExplodedBasicBlock> ctx,
			TabulationResult<BasicBlockInContext<IExplodedBasicBlock>, CGNode, DomainElement> flowResult,
			IFDSTaintDomain<IExplodedBasicBlock> domain) {
		Set<FlowType<IExplodedBasicBlock>> sources = Sets.newHashSet();

		final CodeElement localElt = new LocalElement(ssaVal);
		Set<CodeElement> elts = Sets.newHashSet(localElt);

		final CGNode node = block.getNode();
		PointerKey pk = ctx.pa.getHeapModel().getPointerKeyForLocal(node,
				ssaVal);
		OrdinalSet<InstanceKey> iks = ctx.pa.getPointsToSet(pk);
		if (null == iks) {
			logger.warn("no instance keys found for SinkPoint {}", this);
		}

		for (InstanceKey ik : iks) {
			elts.addAll(ctx.codeElementsForInstanceKey(ik));
		}
		logger.debug("checking for sources from code elements {}", elts);

		for (CodeElement elt : elts) {
			for (DomainElement de : domain.getPossibleElements(elt)) {
				if (flowResult.getResult(block).contains(
						domain.getMappedIndex(de))) {
					sources.add(de.taintSource);
				}
			}
		}
		return sources;
	}
	
	@Override
	public FlowType<IExplodedBasicBlock> getFlow() {
		return sinkFlow;
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
		LocalSinkPoint other = (LocalSinkPoint) obj;
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