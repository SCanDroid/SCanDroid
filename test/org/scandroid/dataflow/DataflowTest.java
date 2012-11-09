package org.scandroid.dataflow;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
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
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.types.TypeName;


@RunWith(Parameterized.class)
public class DataflowTest {
	private static final Logger logger = LoggerFactory
			.getLogger(DataflowTest.class);

	private static AndroidAnalysisContext analysisContext;
	private static DataflowResults gold;

	/**
	 * Path to the original natives.xml file.
	 * 
	 * This assumes that the wala source is in wala/wala-src
	 */
	private static final String TEST_DATA_DIR = "data/testdata/";
	private static final String TEST_JAR = TEST_DATA_DIR
			+ "testJar-1.0-SNAPSHOT.jar";
	
	/**
     * Hack alert: since @Parameters-annotated methods are run before every
     * other JUnit method, we have to do setup of the analysis context here
     */
    @Parameters(name = "{0}")
    public static Collection<Object[]> setup() throws Throwable {
        //ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) 
        //        LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        //root.setLevel(Level.TRACE);
        List<Object[]> entrypoints = Lists.newArrayList();
        gold = new DataflowResults();

        analysisContext = new AndroidAnalysisContext(
                new DefaultSCanDroidOptions() {
                    @Override
                    public URI getClasspath() {
                        return new File(TEST_JAR).toURI();
                    }

                    @Override
                    public URI getSummariesURI() {
                        return null;
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
            if (!clname.getPackage().toString().startsWith("org/scandroid/testing")) {
                continue;
            }
            logger.debug("Adding entrypoints from {}", clazz);
            logger.debug("abstract={}", clazz.isAbstract());
            for (IMethod method : clazz.getAllMethods()) {
                String desc = method.getSignature();
                if(!gold.expectedResults.containsKey(desc)) {
                    logger.debug("Skipping {} due to lack of output information.", desc);
                    continue;
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
                    logger.debug("abstract={}, static={}, init={}, clinit={}, synthetic={}", method.isAbstract(), method.isStatic(), method.isInit(), method.isClinit(), method.isSynthetic());
                    String junitDesc = isEclipse() ? URLEncoder.encode(method.getSignature(), "UTF-8") : method.getSignature();
                    entrypoints.add(new Object[] {
                            junitDesc,
                            new DefaultEntrypoint(method, cha) });
                }
            }
        }
//      System.exit(0);
        return entrypoints;
    }

    private static boolean isEclipse() {
        final String command = System.getProperty("sun.java.command");
        return command != null && command.startsWith("org.eclipse.jdt.internal.junit.runner.RemoteTestRunner");
    }

    public final Entrypoint entrypoint;

    /**
     * @param methodDescriptor
     *            used to name tests
     * @param entrypoint
     *            the method to test
     */
    public DataflowTest(String methodDescriptor, Entrypoint entrypoint) {
        this.entrypoint = entrypoint;
    }

    @Test
    public void testDataflow() throws Throwable {
        CGAnalysisContext<IExplodedBasicBlock> ctx = new CGAnalysisContext<IExplodedBasicBlock>(
                analysisContext, new IEntryPointSpecifier() {
                    @Override
                    public List<Entrypoint> specify(
                            AndroidAnalysisContext analysisContext) {
                        return Lists.newArrayList(entrypoint);
                    }
                });
        ISpecs specs = TestSpecs.specsFromDescriptor(ctx.getClassHierarchy(), entrypoint.getMethod().getSignature());

        Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>> dfResults =
                runDFAnalysis(ctx, specs);

        Set<String> flows = Sets.newHashSet();
        for(FlowType<IExplodedBasicBlock> src : dfResults.keySet()) {
            for(FlowType<IExplodedBasicBlock> dst : dfResults.get(src)) {
                flows.add(src.descString() + " -> " + dst.descString());
            }
        }
        Assert.assertEquals(gold.expectedResults.get(entrypoint.getMethod().getSignature()), flows);
    }
    
    private Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>> runDFAnalysis(
            CGAnalysisContext<IExplodedBasicBlock> cgContext, ISpecs specs)
            throws IOException, ClassHierarchyException,
            CallGraphBuilderCancelException {

        Map<BasicBlockInContext<IExplodedBasicBlock>, Map<FlowType<IExplodedBasicBlock>, Set<CodeElement>>> initialTaints = InflowAnalysis
                .analyze(cgContext, new HashMap<InstanceKey, String>(), specs);

        System.out.println("  InitialTaints: " + initialTaints);

        IFDSTaintDomain<IExplodedBasicBlock> domain = new IFDSTaintDomain<IExplodedBasicBlock>();
        TabulationResult<BasicBlockInContext<IExplodedBasicBlock>, CGNode, DomainElement> flowResult = FlowAnalysis
                .analyze(cgContext, initialTaints, domain, null);

        Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>> permissionOutflow = OutflowAnalysis
                .analyze(cgContext, flowResult, domain, specs);

        return permissionOutflow;
    }
}
