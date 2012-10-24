package synthMethod;

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
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.scandroid.MethodSummarySpecs;
import org.scandroid.Summarizer;
import org.scandroid.util.IEntryPointSpecifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spec.ISpecs;
import util.AndroidAnalysisContext;
import util.CGAnalysisContext;

import ch.qos.logback.classic.Level;

import com.google.common.collect.Lists;
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

import domain.CodeElement;
import domain.DomainElement;
import domain.IFDSTaintDomain;
import flow.FlowAnalysis;
import flow.InflowAnalysis;
import flow.OutflowAnalysis;
import flow.types.FlowType;

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
		ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
//		root.setLevel(Level.TRACE);
		List<Object[]> entrypoints = Lists.newArrayList();

		analysisContext = new AndroidAnalysisContext(
				new DefaultSCanDroidOptions() {
					@Override
					public URI getClasspath() {
						return new File(TEST_JAR).toURI();
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
				if (!declClass.getName().toString().endsWith("LLTestIter"))
					continue;
				if (method.isAbstract() || method.isSynthetic()
						|| (declClass.isAbstract() && method.isInit())
						|| (declClass.isAbstract() && !method.isStatic())) {
					continue;
				}
				if (method.getDeclaringClass().getClassLoader().getReference()
						.equals(scope.getApplicationLoader())) {
					logger.debug("Adding entrypoint for {}", method);
					logger.debug("abstract={}, static={}, init={}, clinit={}, synthetic={}", method.isAbstract(), method.isStatic(), method.isInit(), method.isClinit(), method.isSynthetic());
					entrypoints.add(new Object[] {
							isEclipse() ? URLEncoder.encode(method.getSignature(), "UTF-8") : method.getSignature(),
							new DefaultEntrypoint(method, cha) });
				}
			}
		}
//		System.exit(0);
		return entrypoints;
	}
	
	private static boolean isEclipse() {
		final String command = System.getProperty("sun.java.command");
		return command != null && command.startsWith("org.eclipse.jdt.internal.junit.runner.RemoteTestRunner");
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
		File summaryFile = new File(".", // FileUtils.getTempDirectory(),
				summaryFileName());
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
		Assert.assertEquals("Results differed with summaries", directResults,
				summarizedResults);
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

		Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>> permissionOutflow = OutflowAnalysis
				.analyze(cgContext, flowResult, domain, specs);

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
}
