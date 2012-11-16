/**
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
package org.scandroid.dataflow;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.scandroid.domain.CodeElement;
import org.scandroid.domain.DomainElement;
import org.scandroid.domain.IFDSTaintDomain;
import org.scandroid.flow.FlowAnalysis;
import org.scandroid.flow.InflowAnalysis;
import org.scandroid.flow.OutflowAnalysis;
import org.scandroid.flow.types.FlowType;
import org.scandroid.spec.ISpecs;
import org.scandroid.synthmethod.DefaultSCanDroidOptions;
import org.scandroid.synthmethod.TestSpecs;
import org.scandroid.util.AndroidAnalysisContext;
import org.scandroid.util.CGAnalysisContext;
import org.scandroid.util.IEntryPointSpecifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.ibm.wala.cfg.ControlFlowGraph;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IClassLoader;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.dataflow.IFDS.ICFGSupergraph;
import com.ibm.wala.dataflow.IFDS.TabulationResult;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.viz.DotUtil;
import com.ibm.wala.viz.NodeDecorator;

@RunWith(Parameterized.class)
public class DataflowTest {
	private static final Logger logger = LoggerFactory
			.getLogger(DataflowTest.class);

	private static AndroidAnalysisContext analysisContext;
	private static final DataflowResults gold = new DataflowResults();

	private static Set<String> checklist;

	/**
	 * Path to the original natives.xml file.
	 * 
	 * This assumes that the wala source is in wala/wala-src
	 */
	private static final String TEST_DATA_DIR = "data/testdata/";
	private static final String TEST_JAR = TEST_DATA_DIR
			+ "testJar-1.0-SNAPSHOT.jar";

	private static final boolean DEBUG_CFG = false;

	/**
	 * Hack alert: since @Parameters-annotated methods are run before every
	 * other JUnit method, we have to do setup of the analysis context here
	 */
	@Parameters(name = "{0}")
	public static Collection<Object[]> setup() throws Throwable {
		ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory
				.getLogger(Logger.ROOT_LOGGER_NAME);
		root.setLevel(Level.INFO);
		// root.setLevel(Level.DEBUG);
		List<Object[]> entrypoints = Lists.newArrayList();

		checklist = gold.expectedMethods();
		final URI summaries = DataflowTest.class.getResource(
				"/data/MethodSummaries.xml").toURI();

		analysisContext = new AndroidAnalysisContext(
				new DefaultSCanDroidOptions() {
					@Override
					public URI getClasspath() {
						return new File(TEST_JAR).toURI();
					}

					@Override
					public URI getSummariesURI() {
						return summaries;
					}

					@Override
					public boolean stdoutCG() {
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
			TypeName clname = clazz.getName();
			if (!clname.getPackage().toString()
					.startsWith("org/scandroid/testing")) {
				continue;
			}
			logger.debug("Adding entrypoints from {}", clazz);
			logger.debug("abstract={}", clazz.isAbstract());
			for (IMethod method : clazz.getAllMethods()) {
				String desc = method.getSignature();
				if (!gold.describesFlow(desc)) {
					logger.debug(
							"Skipping {} due to lack of output information.",
							desc);
					continue;
				} else {
					logger.debug("Testing {} for data flow.", desc);
					checklist.remove(desc);
				}
				IClass declClass = method.getDeclaringClass();
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
					String junitDesc = isEclipse() ? URLEncoder.encode(
							method.getSignature(), "UTF-8") : method
							.getSignature();
					entrypoints.add(new Object[] { junitDesc,
							new DefaultEntrypoint(method, cha) });
				}
			}
		}

		if (checklist.size() != 0) {
			logger.error("Expected methods to test that could not be found: ");
			for (String desc : checklist) {
				logger.error("\t {}", desc);

				// if we can't find the description, create a null test:
				String junitDesc = isEclipse() ? URLEncoder.encode(desc,
						"UTF-8") : desc;
				entrypoints.add(new Object[] { junitDesc, null });
			}
		}

		return entrypoints;
	}

	@AfterClass
	public static void tearDown() {

	}

	private static boolean isEclipse() {
		final String command = System.getProperty("sun.java.command");
		return command != null
				&& command
						.startsWith("org.eclipse.jdt.internal.junit.runner.RemoteTestRunner");
	}

	public final Entrypoint entrypoint;

	private String descriptor;

	/**
	 * @param methodDescriptor
	 *            used to name tests
	 * @param entrypoint
	 *            the method to test
	 */
	public DataflowTest(String methodDescriptor, Entrypoint entrypoint) {
		this.descriptor = methodDescriptor;
		this.entrypoint = entrypoint;
	}

	@Test
	public void testDataflow() throws Throwable {
		Assert.assertNotNull(
				"Could not find method to test for: " + descriptor, entrypoint);

		CGAnalysisContext<IExplodedBasicBlock> ctx = new CGAnalysisContext<IExplodedBasicBlock>(
				analysisContext, new IEntryPointSpecifier() {
					@Override
					public List<Entrypoint> specify(
							AndroidAnalysisContext analysisContext) {
						return Lists.newArrayList(entrypoint);
					}
				});
		// logger.warn("Heap dump:");
		// for (PointerKey pk : ctx.pa.getPointerKeys()) {
		// logger.warn("{}", pk);
		// for (InstanceKey ik : ctx.pa.getPointsToSet(pk)) {
		// logger.warn("\t{}", ik);
		// }
		// }
		if (DEBUG_CFG) {
			for (CGNode node : ctx.cg.getNodes(entrypoint.getMethod()
					.getReference())) {
				logger.debug(Arrays.toString(node.getIR().getInstructions()));
				ICFGSupergraph graph = (ICFGSupergraph) ctx.graph;
				ControlFlowGraph<SSAInstruction, IExplodedBasicBlock> cfg = graph
						.getCFG(graph.getLocalBlock(node, 0));
				final String name = entrypoint.getMethod().getName().toString();
				logger.debug("outputting full graph dot to {}", name
						+ ".full.dot");
				DotUtil.writeDotFile(graph, new NodeDecorator() {

					@Override
					public String getLabel(Object o) throws WalaException {
						BasicBlockInContext<IExplodedBasicBlock> block = (BasicBlockInContext<IExplodedBasicBlock>) o;
						final SSAInstruction inst = block.getLastInstruction();
						final String instString = inst == null ? "NULL" : inst
								.toString();
						String label = String.format("Method %s\nBlock %d: %s",
								block.getMethod().getSignature(),
								block.getNumber(), instString);
						return label;
					}
				}, name, name + ".full.dot");

				logger.debug("outputting method graph dot to {}", name + ".dot");
				DotUtil.writeDotFile(cfg, new NodeDecorator() {

					@Override
					public String getLabel(Object o) throws WalaException {
						IExplodedBasicBlock block = (IExplodedBasicBlock) o;
						final SSAInstruction inst = block.getLastInstruction();
						final String instString = inst == null ? "NULL" : inst
								.toString();
						String label = String.format("Block %d: %s",
								block.getNumber(), instString);
						return label;
					}
				}, name, name + ".dot");
			}
		}
		ISpecs specs = TestSpecs.specsFromDescriptor(ctx.getClassHierarchy(),
				entrypoint.getMethod().getSignature());

		Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>> dfResults = runDFAnalysis(
				ctx, specs);

		Set<String> flows = Sets.newHashSet();
		for (FlowType<IExplodedBasicBlock> src : dfResults.keySet()) {
			for (FlowType<IExplodedBasicBlock> dst : dfResults.get(src)) {
				final String lhs = src.descString();
				final String rhs = dst.descString();
				if (!lhs.equals(rhs)) {
					// suppress identity edges
					flows.add(lhs + " -> " + rhs);
				}
			}
		}
		Assert.assertEquals(
				gold.getFlows(entrypoint.getMethod().getSignature()), flows);
	}

	private Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>> runDFAnalysis(
			CGAnalysisContext<IExplodedBasicBlock> cgContext, ISpecs specs)
			throws IOException, ClassHierarchyException,
			CallGraphBuilderCancelException {

		Map<BasicBlockInContext<IExplodedBasicBlock>, Map<FlowType<IExplodedBasicBlock>, Set<CodeElement>>> initialTaints = InflowAnalysis
				.analyze(cgContext, new HashMap<InstanceKey, String>(), specs);

		logger.debug("  InitialTaints: {}", initialTaints);

		IFDSTaintDomain<IExplodedBasicBlock> domain = new IFDSTaintDomain<IExplodedBasicBlock>();
		TabulationResult<BasicBlockInContext<IExplodedBasicBlock>, CGNode, DomainElement> flowResult = FlowAnalysis
				.analyze(cgContext, initialTaints, domain, null);

		Iterator<DomainElement> it = domain.iterator();
		while (it.hasNext()) {
			logger.debug("{}", it.next());
		}

		Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>> permissionOutflow = new OutflowAnalysis(
				cgContext, specs).analyze(flowResult, domain);

		return permissionOutflow;
	}
}
