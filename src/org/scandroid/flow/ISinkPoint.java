package org.scandroid.flow;

import java.util.Set;

import org.scandroid.domain.DomainElement;
import org.scandroid.domain.IFDSTaintDomain;
import org.scandroid.flow.types.FlowType;
import org.scandroid.util.CGAnalysisContext;

import com.ibm.wala.dataflow.IFDS.TabulationResult;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;

public interface ISinkPoint {
	
	public Set<FlowType<IExplodedBasicBlock>> findSources(
			CGAnalysisContext<IExplodedBasicBlock> ctx,
			TabulationResult<BasicBlockInContext<IExplodedBasicBlock>, CGNode, DomainElement> flowResult,
			IFDSTaintDomain<IExplodedBasicBlock> domain);

	public FlowType<IExplodedBasicBlock> getFlow();
}
