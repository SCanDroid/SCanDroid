package com.ibm.wala.ipa.callgraph.propagation.cfa;

import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.ContextSelector;
import com.ibm.wala.ipa.callgraph.impl.DexExplicitCallGraph;
import com.ibm.wala.ipa.callgraph.impl.ExplicitCallGraph;
import com.ibm.wala.ipa.callgraph.propagation.SSAContextInterpreter;
import com.ibm.wala.ipa.cha.IClassHierarchy;

public class DexSSAPropagationCallGraphBuilder extends
	ZeroXCFABuilder {
	

	
	public DexSSAPropagationCallGraphBuilder(IClassHierarchy cha,
			AnalysisOptions options, AnalysisCache cache,
			ContextSelector appContextSelector,
			SSAContextInterpreter appContextInterpreter, int instancePolicy) {
		super(cha, options, cache, appContextSelector, appContextInterpreter,
				instancePolicy);
	}

	@Override
	protected ExplicitCallGraph createEmptyCallGraph(IClassHierarchy cha, AnalysisOptions options) {
		return new DexExplicitCallGraph(cha, options, getAnalysisCache());
	}

}
