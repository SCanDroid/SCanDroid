package com.ibm.wala.ipa.callgraph.propagation.cfa;

import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.ContextSelector;
import com.ibm.wala.ipa.callgraph.impl.DexExplicitCallGraph;
import com.ibm.wala.ipa.callgraph.impl.ExplicitCallGraph;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKeyFactory;
import com.ibm.wala.ipa.callgraph.propagation.SSAContextInterpreter;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.cha.IClassHierarchy;

public class DexSSAPropagationCallGraphBuilder extends
	SSAPropagationCallGraphBuilder {
	

	
	public DexSSAPropagationCallGraphBuilder(IClassHierarchy cha,
			AnalysisOptions options, AnalysisCache cache,
			ContextSelector appContextSelector,
			SSAContextInterpreter appContextInterpreter, InstanceKeyFactory instanceKeys) {
	    super(cha, options, cache, new DefaultPointerKeyFactory());
	    setContextSelector(appContextSelector);
	    setContextInterpreter(appContextInterpreter);
	    setInstanceKeys(instanceKeys);
	}

	@Override
	protected ExplicitCallGraph createEmptyCallGraph(IClassHierarchy cha, AnalysisOptions options) {
		return new DexExplicitCallGraph(cha, options, getAnalysisCache());
	}

}
