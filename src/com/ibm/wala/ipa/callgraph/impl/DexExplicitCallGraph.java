package com.ibm.wala.ipa.callgraph.impl;

import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.util.CancelException;

public class DexExplicitCallGraph extends ExplicitCallGraph {

	public DexExplicitCallGraph(IClassHierarchy cha, AnalysisOptions options,
			AnalysisCache cache) {
		super(cha, options, cache);
	}
	
	@Override
	protected CGNode makeFakeRootNode() throws CancelException {
		return findOrCreateNode(new DexFakeRootMethod(cha, options, getAnalysisCache()), Everywhere.EVERYWHERE);
	}

}
