package org.scandroid.util;

import java.net.URI;

import com.ibm.wala.ipa.callgraph.AnalysisOptions.ReflectionOptions;

/**
 * @author acfoltzer
 * 
 *         An abstraction of the options for a SCanDroid execution
 */
public interface ISCanDroidOptions {

	/**
	 * @return whether to create a full call graph pdf
	 */
	public boolean pdfCG();

	/**
	 * @return whether to create an application-only call graph pdf
	 */
	public boolean pdfPartialCG();

	/**
	 * @return whether to create a call graph of application + 1 level of system
	 *         calls
	 */
	public boolean pdfOneLevelCG();

	/**
	 * @return whether to create a system + 1 level of application call graph
	 */
	public boolean systemToApkCG();

	/**
	 * @return whether to print a full call graph to stdout
	 */
	public boolean stdoutCG();

	/**
	 * @return whether to include the Android library in flow analysis
	 */
	public boolean includeLibrary();

	/**
	 * @return whether to analyze each entry point separately
	 */
	public boolean separateEntries();

	/**
	 * @return whether to bring up a GUI to analyze domain elements for flow
	 *         analysis
	 */
	public boolean ifdsExplorer();

	/**
	 * @return whether to look for main methods and add them as entry points
	 */
	public boolean addMainEntrypoints();

	/**
	 * @return whether to use ServerThread.run as the entry point for analysis
	 */
	public boolean useThreadRunMain();

	/**
	 * @return whether to run string prefix analysis
	 */
	public boolean stringPrefixAnalysis();

	/**
	 * @return whether to stop after generating the call graph
	 */
	public boolean testCGBuilder();

	/**
	 * @return whether to log class hierarchy warnings
	 */
	public boolean classHierarchyWarnings();

	/**
	 * @return whether to log call graph builder warnings
	 */
	public boolean cgBuilderWarnings();

	/**
	 * @return whether to check conformance to built-in policy
	 */
	public boolean useDefaultPolicy();

	/**
	 * @return the URI pointing to the jar or apk to analyze
	 */
	public URI getClasspath();

	/**
	 * @return the filename portion of the classpath to analyze
	 */
	public String getFilename();

	/**
	 * @return a URI to the Android library jar
	 */
	public URI getAndroidLibrary();

	/**
	 * @return the ReflectionOptions for this run
	 */
	public ReflectionOptions getReflectionOptions();

	/**
	 * @return a URI to the XML method summaries file
	 */
	public URI getSummariesURI();

}
