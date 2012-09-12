package synthMethod;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import spec.CallArgSinkSpec;
import spec.EntryArgSourceSpec;
import spec.ISpecs;
import spec.MethodNamePattern;
import spec.SinkSpec;
import spec.SourceSpec;
import util.AndroidAppLoader;
import util.LoaderUtils;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.dataflow.IFDS.ICFGSupergraph;
import com.ibm.wala.dataflow.IFDS.ISupergraph;
import com.ibm.wala.dataflow.IFDS.TabulationResult;
import com.ibm.wala.dex.util.config.DexAnalysisScopeReader;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.util.Predicate;

import domain.CodeElement;
import domain.DomainElement;
import domain.IFDSTaintDomain;
import flow.FlowAnalysis;
import flow.InflowAnalysis;
import flow.OutflowAnalysis;
import flow.types.FlowType;

public class MethodAnalysisTest {

    /**
     * Path to the original natives.xml file.
     * 
     * This assumes that the wala source is in wala/wala-src
     */
    public static final String WALA_NATIVES_XML = 
            "wala/wala-src/com.ibm.wala.core/dat/natives.xml";
    private static final String TEST_DATA_DIR = "data/testdata/";

    @Test
    public final void test_summarizeProducesOutput() 
            throws IllegalArgumentException, CallGraphBuilderCancelException,
            IOException, ClassHierarchyException {

        String appJar = TEST_DATA_DIR + File.separator + "trivialJar1-1.0-SNAPSHOT.jar";
        String filename = summarize(appJar);
        
        String contents = readFile(filename);
        System.out.println("-----     Summary File: -------");
        System.out.println(contents);
        System.out.println("-----   End Summary File -------");
        Assert.assertTrue("contents not long enough.", 80 <= contents.length());
    }
    
    /**
     * Simple, direct data flow through a few methods.
     * 
     * @throws IllegalArgumentException
     * @throws CallGraphBuilderCancelException
     * @throws IOException
     * @throws ClassHierarchyException
     */
    @Test
    public final void test_trivialJar1() 
            throws IllegalArgumentException, CallGraphBuilderCancelException,
            IOException, ClassHierarchyException {

        String appJar = TEST_DATA_DIR + File.separator + "trivialJar1-1.0-SNAPSHOT.jar";
        runOnJar(appJar, new TestSpecs());
    }

    /**
     * Trivial Jar 2 uses simple cons-style lists to experiment with recursion and field access on objects.
     * 
     * @throws IllegalArgumentException
     * @throws CallGraphBuilderCancelException
     * @throws IOException
     * @throws ClassHierarchyException
     */
    @Test
    public final void test_consCellRecursion() 
            throws IllegalArgumentException, CallGraphBuilderCancelException,
            IOException, ClassHierarchyException {

        String appJar = TEST_DATA_DIR + File.separator + "trivialJar2-1.0-SNAPSHOT.jar";
        runOnJar(appJar, new TestSpecs());
    }
    
    /**
     * TrivialJar3 uses .length on an array (incoming params to main(String[] args))
     * as a source, but this is tracked differently than using a param value directly (or, eg. args[0])
     * 
     * 
     * @throws IllegalArgumentException
     * @throws CallGraphBuilderCancelException
     * @throws IOException
     * @throws ClassHierarchyException
     */
    @Test
    public final void test_flowFromInputParameter() 
            throws IllegalArgumentException, CallGraphBuilderCancelException,
            IOException, ClassHierarchyException {

        String appJar = TEST_DATA_DIR + File.separator + "trivialJar3-1.0-SNAPSHOT.jar";
        runOnJar(appJar, new TestSpecs());
    }
    /**
     * Trivial Jar 4 uses a static field in data flow.
     * 
     * @throws IllegalArgumentException
     * @throws CallGraphBuilderCancelException
     * @throws IOException
     * @throws ClassHierarchyException
     */
    @Test
    public final void test_staticFieldsinDataFlow() 
            throws IllegalArgumentException, CallGraphBuilderCancelException,
            IOException, ClassHierarchyException {

        String appJar = TEST_DATA_DIR + File.separator + "trivialJar4-1.0-SNAPSHOT.jar";
        runOnJar(appJar, new TestSpecs());
    }
    
   // @Test
    public final void test_summarizeScandroid() 
            throws IllegalArgumentException, CallGraphBuilderCancelException,
            IOException, ClassHierarchyException {

        String appJar = TEST_DATA_DIR + File.separator + "sap.jar";
        runOnJar(appJar, new ISpecs() {
            @Override
            public MethodNamePattern[] getEntrypointSpecs() {
                return null;
            }
            
            @Override
            public SourceSpec[] getSourceSpecs() {
                return new SourceSpec[] { 
                         new EntryArgSourceSpec(new MethodNamePattern(
                           "Lorg/scandroid/SeparateEntryAnalysis", "main"),
                           new int[] { })
                         };
            }

            @Override
            public SinkSpec[] getSinkSpecs() {
                return new SinkSpec[] { 
                        new CallArgSinkSpec(new MethodNamePattern(
                          "Ljava/io/PrintStream", "println"), new int[] { }) };
            }
        });
    }
    
    @Test(expected=AssertionError.class)
    public final void test_brokensummaryBreaksDataFlow() 
            throws IllegalArgumentException, CallGraphBuilderCancelException,
            IOException, ClassHierarchyException {
        
        String appJar = "data/testdata/trivialJar1-1.0-SNAPSHOT.jar";

        checkSummaryProperty(appJar, new TestSpecs(), "data/testdata/brokenSummary.xml");
    }
    
    private void runOnJar(String appJar, ISpecs specs) throws IOException,
            ClassHierarchyException, CallGraphBuilderCancelException {
        String summary = summarize(appJar);
        checkSummaryProperty(appJar, specs, summary);
    }

    /**
     * Analyze jarFile without any summarization, record the time taken and 
     * the data flows found.
     * 
     * Analyze jarFile with the provided summaries, record the time taken and
     * the data flows found.
     * 
     * Ensure that the data flows are equal.
     * 
     * TODO: Ensure the time reduces with the use of summaries.
     * 
     * @param jarFile
     * @param summaries
     * @throws IOException 
     * @throws CallGraphBuilderCancelException 
                directResults
     * @throws ClassHierarchyException 
     */
    private void checkSummaryProperty(String jarFile, ISpecs specs, String summaryFile) 
            throws ClassHierarchyException, CallGraphBuilderCancelException, IOException {
        Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>>
          directResults = runDFAnalysis(jarFile, specs, WALA_NATIVES_XML);
        
        Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>>
          summarizedResults = runDFAnalysis(jarFile, specs, summaryFile);
        
        Assert.assertNotSame("No flows found in direct results.", 0, directResults.size());
        Assert.assertNotSame("No flows found in summarized results.", 0, summarizedResults.size());
        Assert.assertEquals("Results differed with summaries", 
                directResults, summarizedResults);
    }
    
    /**
     * Generate summaries for all the entry points in a jar file, write the 
     * summaries to a file, and return the file name.
     * 
     * @param jarFile
     * @return The filename that the summaries were written to.
     * @throws IOException 
     * @throws ClassHierarchyException 
     * @throws CallGraphBuilderCancelException 
     * @throws IllegalArgumentException 
     */
    private String summarize(String jarFile) 
        throws IOException, ClassHierarchyException, IllegalArgumentException, 
               CallGraphBuilderCancelException {
        
        @SuppressWarnings("unchecked")
        MethodAnalysis<IExplodedBasicBlock> methodAnalysis =
                new MethodAnalysis<IExplodedBasicBlock>(Predicate.TRUE);

        AnalysisScope scope = 
                DexAnalysisScopeReader.makeAndroidBinaryAnalysisScope(jarFile, 
                   new File("conf/Java60RegressionExclusions.txt"));
        ClassHierarchy cha = ClassHierarchy.make(scope);
        
        Iterable<Entrypoint> entrypoints = getEntrypoints(scope, cha);
        
        //Iterable<Entrypoint> entrypoints = Util.makeMainEntrypoints(scope, cha);
        AnalysisOptions options = new AnalysisOptions(scope, entrypoints);
        CallGraphBuilder builder = 
           AndroidAppLoader.makeVanillaZeroOneCFABuilder(
                   options, new AnalysisCache(), cha, scope, null, null, WALA_NATIVES_XML);

        CallGraph cg = builder.makeCallGraph(options, null);
        ISupergraph<BasicBlockInContext<IExplodedBasicBlock>, CGNode> sg = 
                ICFGSupergraph.make(cg, builder.getAnalysisCache());
        
        Collection<CGNode> nodes = cg.getEntrypointNodes();
        
        for (Iterator<CGNode> itr = nodes.iterator(); itr.hasNext();) {
            CGNode cgNode = (CGNode) itr.next();
            
            BasicBlockInContext<IExplodedBasicBlock>[] entriesForProcedure =
                    sg.getEntriesForProcedure(cgNode);
            for (int i = 0; i < entriesForProcedure.length; i++) {
                methodAnalysis.analyze(sg, 
                        builder.getPointerAnalysis(),
                        null,
                        entriesForProcedure[i]);
            };
        }
        File tempFile = File.createTempFile("scandroid-summaries", ".xml");
        tempFile.deleteOnExit();
        XMLMethodSummaryWriter.writeXML(methodAnalysis, tempFile);

        return tempFile.getAbsolutePath();
    }

    private Iterable<Entrypoint> getEntrypoints(AnalysisScope scope, ClassHierarchy cha) {
        List<Entrypoint> entrypoints = new ArrayList<Entrypoint>();
        for (IClass iClass : cha) {
            for (Iterator<IMethod> itr = iClass.getAllMethods().iterator(); itr.hasNext();) {
                IMethod iMethod = itr.next();
                
                if ( LoaderUtils.fromLoader(iMethod, ClassLoaderReference.Application) ) {
                    entrypoints.add(new DefaultEntrypoint(iMethod, cha));
                }
            }
        }
        //return entrypoints;
        return Util.makeMainEntrypoints(scope, cha);
    }
    
    private
    Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>> 
    runDFAnalysis(String appJar, ISpecs specs, String methodSummariesFile) 
        throws IOException, ClassHierarchyException, 
               CallGraphBuilderCancelException {

        MethodAnalysis<IExplodedBasicBlock> methodAnalysis =
                new MethodAnalysis<IExplodedBasicBlock>();
        AnalysisScope scope = 
                DexAnalysisScopeReader.makeAndroidBinaryAnalysisScope(appJar, 
                   new File("conf/Java60RegressionExclusions.txt"));
        ClassHierarchy cha = ClassHierarchy.make(scope);
        
        Iterable<Entrypoint> entrypoints = getEntrypoints(scope, cha);
        
        AnalysisOptions options = new AnalysisOptions(scope, entrypoints);
        CallGraphBuilder builder = 
           AndroidAppLoader.makeVanillaZeroOneCFABuilder(
                   options, new AnalysisCache(), cha, scope, null, null, methodSummariesFile);

        
        CallGraph cg = builder.makeCallGraph(options, null);

        
        ISupergraph<BasicBlockInContext<IExplodedBasicBlock>, CGNode> sg = 
                ICFGSupergraph.make(cg, builder.getAnalysisCache());
        PointerAnalysis pa = builder.getPointerAnalysis();
        
        Map<BasicBlockInContext<IExplodedBasicBlock>, 
            Map<FlowType<IExplodedBasicBlock>, Set<CodeElement>>> initialTaints = 
              InflowAnalysis.analyze(cg, cha, sg, pa, new HashMap<InstanceKey, String>(), specs);
                   
        IFDSTaintDomain<IExplodedBasicBlock> domain = new IFDSTaintDomain<IExplodedBasicBlock>();
        TabulationResult<BasicBlockInContext<IExplodedBasicBlock>, CGNode, DomainElement> 
          flowResult = FlowAnalysis.analyze(sg, cg, pa, initialTaints, domain, methodAnalysis);

        Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>>
          permissionOutflow = OutflowAnalysis.analyze(cg, cha, sg, pa, flowResult, domain, specs);
        
        return permissionOutflow;
    }
    
    public static String flowMapToString(Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>> flowMap) {
        StringBuilder builder = new StringBuilder();
        
        for (Entry<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>> e : flowMap.entrySet()) {
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

    public String readFile( String file ) throws IOException {
        BufferedReader reader = new BufferedReader( new FileReader (file));
        String         line = null;
        StringBuilder  stringBuilder = new StringBuilder();
        String         ls = System.getProperty("line.separator");

        while( ( line = reader.readLine() ) != null ) {
            stringBuilder.append( line );
            stringBuilder.append( ls );
        }

        return stringBuilder.toString();
    }
}
