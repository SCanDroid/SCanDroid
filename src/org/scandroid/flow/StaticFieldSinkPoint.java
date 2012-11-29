/**
 * 
 */
package org.scandroid.flow;

import java.util.Set;

import org.scandroid.domain.DomainElement;
import org.scandroid.domain.IFDSTaintDomain;
import org.scandroid.domain.StaticFieldElement;
import org.scandroid.flow.types.FlowType;
import org.scandroid.flow.types.StaticFieldFlow;
import org.scandroid.spec.StaticFieldSinkSpec;
import org.scandroid.util.CGAnalysisContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.dataflow.IFDS.TabulationResult;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;

/**
 * @author acfoltzer
 * 
 */
public class StaticFieldSinkPoint implements ISinkPoint {
	private static final Logger logger = LoggerFactory
			.getLogger(StaticFieldSinkPoint.class);

	private final IField field;
	private final FlowType<IExplodedBasicBlock> flow;
	private final BasicBlockInContext<IExplodedBasicBlock> block;

	public StaticFieldSinkPoint(StaticFieldSinkSpec spec,
			BasicBlockInContext<IExplodedBasicBlock> block) {
		this.field = spec.getField();
		this.block = block;
		this.flow = new StaticFieldFlow<IExplodedBasicBlock>(block, field, false);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.scandroid.flow.ISinkPoint#findSources(org.scandroid.util.
	 * CGAnalysisContext, com.ibm.wala.dataflow.IFDS.TabulationResult,
	 * org.scandroid.domain.IFDSTaintDomain)
	 */
	@Override
	public Set<FlowType<IExplodedBasicBlock>> findSources(
			CGAnalysisContext<IExplodedBasicBlock> ctx,
			TabulationResult<BasicBlockInContext<IExplodedBasicBlock>, CGNode, DomainElement> flowResult,
			IFDSTaintDomain<IExplodedBasicBlock> domain) {
		Set<FlowType<IExplodedBasicBlock>> sources = Sets.newHashSet();

		for (DomainElement de : domain
				.getPossibleElements(new StaticFieldElement(field
						.getReference()))) {
			if (de.taintSource instanceof StaticFieldFlow<?>) {
				@SuppressWarnings("unchecked")
				StaticFieldFlow<IExplodedBasicBlock> source = (StaticFieldFlow<IExplodedBasicBlock>) de.taintSource;
				if (source.getField().equals(field)) {
					continue;
				}
			} else if (flowResult.getResult(block).contains(domain.getMappedIndex(de))) {
				sources.add(de.taintSource);
			}
		}

		return sources;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.scandroid.flow.ISinkPoint#getFlow()
	 */
	@Override
	public FlowType<IExplodedBasicBlock> getFlow() {
		return flow;
	}

}
