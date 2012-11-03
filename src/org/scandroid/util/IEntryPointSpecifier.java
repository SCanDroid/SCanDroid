package org.scandroid.util;

import java.util.List;


import com.ibm.wala.ipa.callgraph.Entrypoint;

public interface IEntryPointSpecifier {

	/**
	 * @param analysisContext
	 * @return a list of entrypoints for the given analysis context
	 */
	public List<Entrypoint> specify(AndroidAnalysisContext analysisContext);
}
