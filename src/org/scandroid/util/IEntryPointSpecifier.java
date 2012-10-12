package org.scandroid.util;

import java.util.List;

import util.AndroidAnalysisContext;

import com.ibm.wala.ipa.callgraph.Entrypoint;

public interface IEntryPointSpecifier {

	/**
	 * @param analysisContext
	 * @return a list of entrypoints for the given analysis context
	 */
	public List<Entrypoint> specify(AndroidAnalysisContext analysisContext);
}
