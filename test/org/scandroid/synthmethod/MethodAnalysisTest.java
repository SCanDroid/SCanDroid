/*
 *
 * Copyright (c) 2009-2012,
 *
 *  Galois, Inc. (Aaron Tomb <atomb@galois.com>, Rogan Creswick <creswick@galois.com>)
 *  Steve Suh    <suhsteve@gmail.com>
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
package org.scandroid.synthmethod;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.scandroid.MethodSummarySpecs;
import org.scandroid.Summarizer;
import org.scandroid.dataflow.DataflowTest;
import org.scandroid.domain.CodeElement;
import org.scandroid.domain.DomainElement;
import org.scandroid.domain.IFDSTaintDomain;
import org.scandroid.flow.FlowAnalysis;
import org.scandroid.flow.InflowAnalysis;
import org.scandroid.flow.OutflowAnalysis;
import org.scandroid.flow.types.FlowType;
import org.scandroid.spec.ISpecs;
import org.scandroid.synthmethod.DefaultSCanDroidOptions;
import org.scandroid.util.AndroidAnalysisContext;
import org.scandroid.util.CGAnalysisContext;
import org.scandroid.util.IEntryPointSpecifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IClassLoader;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.dataflow.IFDS.TabulationResult;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.summaries.MethodSummary;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;

@RunWith(Parameterized.class)
public class MethodAnalysisTest {
	private static final Logger logger = LoggerFactory
			.getLogger(MethodAnalysisTest.class);

	private static AndroidAnalysisContext analysisContext;

	/**
	 * Path to the original natives.xml file.
	 * 
	 * This assumes that the wala source is in wala/wala-src
	 */
	public static final String WALA_NATIVES_XML = "data/MethodSummaries.xml";
	private static final String TEST_DATA_DIR = "data/testdata/";
	private static final String TEST_JAR = TEST_DATA_DIR
			+ "testJar-1.0-SNAPSHOT.jar";

	/**
	 * Hack alert: since @Parameters-annotated methods are run before every
	 * other JUnit method, we have to do setup of the analysis context here
	 */
	@Parameters(name = "{0}")
	public static Collection<Object[]> setup() throws Throwable {
		ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory
				.getLogger(Logger.ROOT_LOGGER_NAME);
		// root.setLevel(Level.TRACE);
		List<Object[]> entrypoints = Lists.newArrayList();

		analysisContext = new AndroidAnalysisContext(
				new DefaultSCanDroidOptions() {
					@Override
					public URI getClasspath() {
						return new File(TEST_JAR).toURI();
					}

					@Override
					public boolean stdoutCG() {
						// TODO Auto-generated method stub
						return false;
					}
				});
		final AnalysisScope scope = analysisContext.getScope();
		final ClassHierarchy cha = analysisContext.getClassHierarchy();
		IClassLoader loader = cha.getFactory().getLoader(
				scope.getApplicationLoader(), cha, scope);
		logger.debug("Found {} classes in Applicaton scope",
				loader.getNumberOfClasses());

		Iterator<IClass> classIt = loader.iterateAllClasses();
		while (classIt.hasNext()) {
			IClass clazz = classIt.next();
			if (clazz.isInterface()) {
				continue;
			}
			logger.debug("Adding entrypoints from {}", clazz);
			logger.debug("abstract={}", clazz.isAbstract());
			for (IMethod method : clazz.getAllMethods()) {
				IClass declClass = method.getDeclaringClass();
//				if (!declClass.getName().toString().endsWith("LLTestIter"))
//					continue;
				if (method.isAbstract() || method.isSynthetic()
						|| (declClass.isAbstract() && method.isInit())
						|| (declClass.isAbstract() && !method.isStatic())) {
					continue;
				}
				if (method.getDeclaringClass().getClassLoader().getReference()
						.equals(scope.getApplicationLoader())) {
					logger.debug("Adding entrypoint for {}", method);
					logger.debug(
							"abstract={}, static={}, init={}, clinit={}, synthetic={}",
							method.isAbstract(), method.isStatic(),
							method.isInit(), method.isClinit(),
							method.isSynthetic());
					entrypoints.add(new Object[] {
							DataflowTest.refineDescription(method
									.getSignature()),
							new DefaultEntrypoint(method, cha) });
				}
			}
		}
		// System.exit(0);
		return entrypoints;
	}

	public final Entrypoint entrypoint;

	private InputStream summaryStream;

	/**
	 * @param methodDescriptor
	 *            only used to name tests
	 * @param entrypoint
	 *            the method to test
	 */
	public MethodAnalysisTest(String methodDescriptor, Entrypoint entrypoint) {
		this.entrypoint = entrypoint;
	}

	@Before
	public void makeSummary() throws Throwable {
		Summarizer summarizer = new Summarizer(TEST_JAR);
		summarizer.summarize(entrypoint.getMethod().getSignature());
		File summaryFile = new File(FileUtils.getTempDirectory(),
				summaryFileName());
		summaryFile.deleteOnExit();
		FileUtils.writeStringToFile(summaryFile, summarizer.serialize());
		summaryStream = FileUtils.openInputStream(summaryFile);
	}

	@After
	public void closeSummary() throws Throwable {
		if (summaryStream != null)
			summaryStream.close();
	}

	private String summaryFileName() throws Throwable {
		return "summary-"
				+ URLEncoder.encode(entrypoint.getMethod().getSignature(),
						"UTF-8");
	}

	@Test
	public void testSummary() throws Throwable {
		CGAnalysisContext<IExplodedBasicBlock> noSummaryContext = new CGAnalysisContext<IExplodedBasicBlock>(
				analysisContext, new IEntryPointSpecifier() {
					@Override
					public List<Entrypoint> specify(
							AndroidAnalysisContext analysisContext) {
						return Lists.newArrayList(entrypoint);
					}
				});

		CGAnalysisContext<IExplodedBasicBlock> summaryContext = new CGAnalysisContext<IExplodedBasicBlock>(
				analysisContext, new IEntryPointSpecifier() {
					@Override
					public List<Entrypoint> specify(
							AndroidAnalysisContext analysisContext) {
						return Lists.newArrayList(entrypoint);
					}
				}, Lists.newArrayList(summaryStream));

		final MethodSummary methodSummary = new MethodSummary(entrypoint
				.getMethod().getReference());
		methodSummary.setStatic(entrypoint.getMethod().isStatic());
		ISpecs specs = new MethodSummarySpecs(methodSummary);

		long startTime = System.currentTimeMillis();

		Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>> directResults = runDFAnalysis(
				noSummaryContext, specs);

		long directRunTime = System.currentTimeMillis() - startTime;

		System.out.println(" ----------------------------------------  ");
		System.out.println(" ---  DIRECT RESULTS DONE             ---  ");
		System.out.println(" ----------------------------------------  ");

		startTime = System.currentTimeMillis();
		Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>> summarizedResults = runDFAnalysis(
				summaryContext, specs);

		long summaryRunTime = System.currentTimeMillis() - startTime;

		System.out.println("Direct runtime: " + directRunTime
				+ " Summary runtime: " + summaryRunTime);
		System.out.println("Speedup of: " + (directRunTime - summaryRunTime)
				+ " ms");

		Assert.assertSame("Flows did not match.", directResults.size(),
				summarizedResults.size());
		System.out.println("Direct Flows: \n" + flowMapToString(directResults));
		System.out.println("Summary Flows: \n"
				+ flowMapToString(summarizedResults));

		Assert.assertTrue("Results differed", equalsModSynthetic(directResults, summarizedResults));
	}

	private Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>> runDFAnalysis(
			CGAnalysisContext<IExplodedBasicBlock> cgContext, ISpecs specs)
			throws IOException, ClassHierarchyException,
			CallGraphBuilderCancelException {

		Map<BasicBlockInContext<IExplodedBasicBlock>, Map<FlowType<IExplodedBasicBlock>, Set<CodeElement>>> initialTaints = InflowAnalysis
				.analyze(cgContext, new HashMap<InstanceKey, String>(), specs);

		System.out.println("  InitialTaints count: " + initialTaints.size());

		IFDSTaintDomain<IExplodedBasicBlock> domain = new IFDSTaintDomain<IExplodedBasicBlock>();
		TabulationResult<BasicBlockInContext<IExplodedBasicBlock>, CGNode, DomainElement> flowResult = FlowAnalysis
				.analyze(cgContext, initialTaints, domain, null);

		Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>> permissionOutflow = new OutflowAnalysis(
				cgContext, specs).analyze(flowResult, domain);

		return permissionOutflow;
	}

	public static String flowMapToString(
			Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>> flowMap) {
		StringBuilder builder = new StringBuilder();

		for (Entry<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>> e : flowMap
				.entrySet()) {
			FlowType<IExplodedBasicBlock> source = e.getKey();

			for (FlowType<IExplodedBasicBlock> sink : e.getValue()) {
				builder.append("source: " + source);
				builder.append(" ->> ");
				builder.append("sink: " + sink);
				builder.append("\n");
			}
		}

		return builder.toString();
	}

	private static boolean equalsModSynthetic(
			Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>> map1,
			Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>> map2) {
		if (map1.size() != map2.size()) {
			return false;
		}
		return equalsModSynthetic(map1.entrySet(), map2.entrySet());
	}

	private static boolean equalsModSynthetic(
			Set<Entry<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>>> entrySet1,
			Set<Entry<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>>> entrySet2) {
		if (entrySet1.size() != entrySet2.size()) {
			return false;
		}
		for (Entry<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>> entry : entrySet1) {
			if (!containsModSynthetic(entry, entrySet2)) {
				return false;
			}
		}
		return true;
	}

	private static boolean containsModSynthetic(
			Entry<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>> entry1,
			Set<Entry<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>>> entrySet2) {
		for (Entry<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>> entry2 : entrySet2) {
			if (equalsModSynthetic(entry1.getKey(), entry2.getKey())
					&& equalsFlowSetsModSynthetic(entry1.getValue(), entry2.getValue())) {
				return true;
			}
		}
		return false;		
	}	

	private static boolean equalsModSynthetic(
			FlowType<IExplodedBasicBlock> flow1,
			FlowType<IExplodedBasicBlock> flow2) {
		if (flow1 == flow2)
			return true;
		if (flow1 == null)
			return false;
		if (flow1.getClass() != flow2.getClass())
			return false;		
		if (flow1.getBlock() == null) {
			if (flow2.getBlock() != null)
				return false;
		} else if (!flow1.getBlock().getMethod().getSignature().equals(flow2.getBlock().getMethod().getSignature())) {
			return false;
		}
		if (flow1.isSource() != flow2.isSource())
			return false;
		return true;
	}
	
	private static boolean equalsFlowSetsModSynthetic(
			Set<FlowType<IExplodedBasicBlock>> set1,
			Set<FlowType<IExplodedBasicBlock>> set2) {
		if (set1.size() != set2.size()) {
			return false;
		}
		for (FlowType<IExplodedBasicBlock> flow : set1) {
			if (containsModSynthetic(flow, set2)) {
				return true;
			}
		}
		return false;
	}

	private static boolean containsModSynthetic(
			FlowType<IExplodedBasicBlock> flow,
			Set<FlowType<IExplodedBasicBlock>> set2) {
		for (FlowType<IExplodedBasicBlock> flow2 : set2) {
			if (equalsModSynthetic(flow, flow2)) {
				return true;
			}			
		}
		return false;
	}
}
