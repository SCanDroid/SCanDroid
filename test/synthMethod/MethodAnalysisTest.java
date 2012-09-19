package synthMethod;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import spec.CallArgSinkSpec;
import spec.CallArgSourceSpec;
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

	private static final Predicate<IMethod> MAIN_METHODS =
			MethodPredicates.isNamed("main");
	
    /**
     * Path to the original natives.xml file.
     * 
     * This assumes that the wala source is in wala/wala-src
     */
    public static final String WALA_NATIVES_XML = 
            "wala/wala-src/com.ibm.wala.core/dat/natives.xml";
    private static final String TEST_DATA_DIR = "data/testdata/";

    /**
     * Test that a dataflow is found when running through a constructor.
     * 
     * This serves as a bit of a sanity check -- if it fails, the other tests
     * are a little suspect (since they use the basic data flow analysis as 
     * one oracle).
     * 
     * @throws ClassHierarchyException
     * @throws IllegalArgumentException
     * @throws CallGraphBuilderCancelException
     * @throws IOException
     */
    @Test
    public final void test_dataFlowThroughConstructor() 
    		throws ClassHierarchyException, IllegalArgumentException, 
    		       CallGraphBuilderCancelException, IOException {
    	String appJar = TEST_DATA_DIR + File.separator + "trivialJar5-1.0-SNAPSHOT.jar";
    	
    	Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>>
          directResults = runDFAnalysis(appJar, new TestSpecs(), WALA_NATIVES_XML, MAIN_METHODS);
    	
    	System.out.println("direct results: "+ flowMapToString(directResults));
    	
    	Assert.assertEquals("Exactly one flow needed", 1, directResults.size());
    }
    
    /**
     * Test that the summarization code actually produces a summary for simple 
     * situtaions.
     * 
     * @throws IllegalArgumentException
     * @throws CallGraphBuilderCancelException
     * @throws IOException
     * @throws ClassHierarchyException
     */
    @Test
    public final void test_summarizeProducesOutput() 
            throws IllegalArgumentException, CallGraphBuilderCancelException,
            IOException, ClassHierarchyException {

        String appJar = TEST_DATA_DIR + File.separator + "trivialJar1-1.0-SNAPSHOT.jar";
        String filename = summarize(appJar, MAIN_METHODS);
        
        String contents = readFile(filename);
        
        System.out.println("-----     Summary File: -------");
        System.out.println(contents);
        System.out.println("-----   End Summary File -------");
        
        Assert.assertTrue("contents not long enough.", 80 <= contents.length());
    }
    
    /**
     * Tests that changing the value of a field taints the object the field is in.
     * 
     * This test specifically exercises the situation where a this object is 
     * modified and returned.
     * 
     * @throws IllegalArgumentException
     * @throws CallGraphBuilderCancelException
     * @throws IOException
     * @throws ClassHierarchyException
     */
    @Test
    public final void test_fieldTaintTaintsThisObject() 
            throws IllegalArgumentException, CallGraphBuilderCancelException,
            IOException, ClassHierarchyException {

        String appJar = TEST_DATA_DIR + File.separator + "trivialJar6-1.0-SNAPSHOT.jar";
        runOnJar(appJar, new TestSpecs(), MAIN_METHODS);
    }

    @Test
    public final void test_fieldTaintTaintsThisObjectSummary() 
            throws IllegalArgumentException, CallGraphBuilderCancelException,
            IOException, ClassHierarchyException {

        String appJar = TEST_DATA_DIR + File.separator + "trivialJar6-1.0-SNAPSHOT.jar";
        
        checkSummaryProperty(appJar, new TestSpecs(),
        		"data/testdata/thisTaintSummary.xml", MAIN_METHODS);
    }
    
    /**
     * Simple, direct data flow through a return.
     * 
     * @throws IllegalArgumentException
     * @throws CallGraphBuilderCancelException
     * @throws IOException
     * @throws ClassHierarchyException
     */
    @Test
    public final void test_trivialReturnFlow() 
            throws IllegalArgumentException, CallGraphBuilderCancelException,
            IOException, ClassHierarchyException {

        String appJar = TEST_DATA_DIR + File.separator + "trivialJar1-1.0-SNAPSHOT.jar";
        runOnJar(appJar, new TestSpecs(), MAIN_METHODS);
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
        runOnJar(appJar, new TestSpecs(), MAIN_METHODS);
    }

    /**
     * Trivial Jar 8 uses simple cons-style lists to experiment with looping and field access on objects.
     *
     * @throws IllegalArgumentException
     * @throws CallGraphBuilderCancelException
     * @throws IOException
     * @throws ClassHierarchyException
     */
    @Test
    public final void test_consCellLooping()
            throws IllegalArgumentException, CallGraphBuilderCancelException,
            IOException, ClassHierarchyException {

        String appJar = TEST_DATA_DIR + File.separator + "trivialJar8-1.0-SNAPSHOT.jar";
        runOnJar(appJar, new TestSpecs(), MAIN_METHODS);
    }

    /**
     * Trivial Jar 7 uses a local variable as part of the data flow.
     *
     * @throws IllegalArgumentException
     * @throws CallGraphBuilderCancelException
     * @throws IOException
     * @throws ClassHierarchyException
     */
    @Test
    public final void test_localVariableFlow()
            throws IllegalArgumentException, CallGraphBuilderCancelException,
            IOException, ClassHierarchyException {

        String appJar = TEST_DATA_DIR + File.separator + "trivialJar7-1.0-SNAPSHOT.jar";
        
        final MethodNamePattern methodNamePattern = new MethodNamePattern(
                "Lorg/scandroid/testing/LocalVarFlow", "flow");
        
		runOnJar(appJar, new TestSpecs() {
            @Override
            public SourceSpec[] getSourceSpecs() {
                return new SourceSpec[] { 
                         new EntryArgSourceSpec(methodNamePattern,
                           new int[] { 0 })
                         };
            }
        }, MethodPredicates.matchesPattern(methodNamePattern));
    }
    
    /**
     * Test summarization for data flow through exceptions.
     *
     * @throws IllegalArgumentException
     * @throws CallGraphBuilderCancelException
     * @throws IOException
     * @throws ClassHierarchyException
     */
    @Test
    public final void test_exceptionFlow()
            throws IllegalArgumentException, CallGraphBuilderCancelException,
            IOException, ClassHierarchyException {

        String appJar = TEST_DATA_DIR + File.separator + "trivialJar7-1.0-SNAPSHOT.jar";
        runOnJar(appJar, new TestSpecs() {
            @Override
            public SourceSpec[] getSourceSpecs() {
                return new SourceSpec[] { 
                         new EntryArgSourceSpec(new MethodNamePattern(
                           "Lorg/scandroid/testing/ExceptionFlow", "exceptionFlow"),
                           new int[] { 0 })
                         };
            }
        }, MethodPredicates.isNamed("exceptionFlow"));
    }
    
    /**
     * Test summarization for data flow through assignment return values.
     *
     * @throws IllegalArgumentException
     * @throws CallGraphBuilderCancelException
     * @throws IOException
     * @throws ClassHierarchyException
     */
    @Test
    public final void test_assignmentReturnValFlow()
            throws IllegalArgumentException, CallGraphBuilderCancelException,
            IOException, ClassHierarchyException {

        String appJar = TEST_DATA_DIR + File.separator + "trivialJar7-1.0-SNAPSHOT.jar";

        final MethodNamePattern methodNamePattern = new MethodNamePattern(
                "Lorg/scandroid/testing/AssignmentReturnValFlow", "flow");
        
		runOnJar(appJar, new TestSpecs() {
            @Override
            public SourceSpec[] getSourceSpecs() {
                return new SourceSpec[] { 
                         new EntryArgSourceSpec(methodNamePattern,
                           new int[] { 0 })
                         };
            }
        }, MethodPredicates.matchesPattern(methodNamePattern));
    }
    
    /**
     * Test to see if methods that invoke sources are summarized properly.
     *
     * @throws IllegalArgumentException
     * @throws CallGraphBuilderCancelException
     * @throws IOException
     * @throws ClassHierarchyException
     */
    @Ignore("takes ~1000 seconds")
    @Test
    public final void test_flowFromInvokedSourceStr()
            throws IllegalArgumentException, CallGraphBuilderCancelException,
            IOException, ClassHierarchyException {

        String appJar = TEST_DATA_DIR + File.separator + "trivialJar10-1.0-SNAPSHOT.jar";
        runOnJar(appJar, new TestSpecs() {
        	@Override
            public SourceSpec[] getSourceSpecs() {
                return new SourceSpec[] { 
                         new EntryArgSourceSpec(new MethodNamePattern(
                           "Lorg/scandroid/testing/App", "load"),
                           new int[] { 0 })
                         };
            }
        }, MAIN_METHODS);
    }
    
    /**
     * Test to see if using a hand-crafted (and somewhat wrong) method summary
     * for AbstractStringBuilder.append(String) works here, and improves performance.
     * 
     * When running with no summaries (ie: WALA_NATIVES_XML) this test takes 
     * about 450 seconds.
     * 
     * Ran in ~154 seconds with the summary (while doing method analysis)
     * 
     * @throws IllegalArgumentException
     * @throws CallGraphBuilderCancelException
     * @throws IOException
     * @throws ClassHierarchyException
     */
    @Test
    public final void test_flowFromInvokedSourceStrWithSummary()
            throws IllegalArgumentException, CallGraphBuilderCancelException,
            IOException, ClassHierarchyException {

        String appJar = TEST_DATA_DIR + File.separator + "trivialJar10-1.0-SNAPSHOT.jar";
        String summaryXMLFile = TEST_DATA_DIR + File.separator + "AbstractStringBuilder-append.xml";

        ISpecs specs = new TestSpecs() {
        	@Override
            public SourceSpec[] getSourceSpecs() {
                return new SourceSpec[] { 
                         new EntryArgSourceSpec(new MethodNamePattern(
                           "Lorg/scandroid/testing/App", "load"),
                           new int[] { 0 })
                         };
            }
        }; 
        
        checkSummaryProperty(appJar, specs, summaryXMLFile, MAIN_METHODS);
//        Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>>
//        	directResults = runDFAnalysis(appJar, specs, WALA_NATIVES_XML, MAIN_METHODS);
//        
//        System.out.println("Direct Results: \n"+flowMapToString(directResults));
//        
//        Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>>
//       	    summaryResults = runDFAnalysis(appJar, specs, summaryXMLFile, MAIN_METHODS);
//        System.out.println("Summary Results: \n"+flowMapToString(summaryResults));
    }
    
    
    /**
     * Test to see if methods that invoke sources are summarized properly.
     *
     * @throws IllegalArgumentException
     * @throws CallGraphBuilderCancelException
     * @throws IOException
     * @throws ClassHierarchyException
     */
    @Test
    public final void test_flowFromInvokedSourceInt()
            throws IllegalArgumentException, CallGraphBuilderCancelException,
            IOException, ClassHierarchyException {

        String appJar = TEST_DATA_DIR + File.separator + "trivialJar6-1.0-SNAPSHOT.jar";
        runOnJar(appJar, new TestSpecs() {
        	@Override
            public SourceSpec[] getSourceSpecs() {
                return new SourceSpec[] { 
                         new EntryArgSourceSpec(new MethodNamePattern(
                           "Lorg/scandroid/testing/App", "mainNoStr"),
                           new int[] { 0 }),
                         new CallArgSourceSpec(new MethodNamePattern(
                           "Lorg/scandroid/testing/App", "loadPoint"),
                           new int[] { 0 })
                         };
            }
        }, MethodPredicates.isNamed("mainNoStr"));
    }
    
    /**
     * TrivialJar3 uses .length on an array (incoming params to main(String[] args))
     * as a source, but this is tracked differently than using a param value 
     * directly (or, eg. args[0])
     * 
     * This is also different than accessing a field on a basic POJO.
     * 
     * @throws IllegalArgumentException
     * @throws CallGraphBuilderCancelException
     * @throws IOException
     * @throws ClassHierarchyException
     */
    @Test
    public final void test_flowFromInputArrayParameter() 
            throws IllegalArgumentException, CallGraphBuilderCancelException,
            IOException, ClassHierarchyException {

    	String appJar = TEST_DATA_DIR + File.separator + "trivialJar3-1.0-SNAPSHOT.jar";
        runOnJar(appJar, new TestSpecs(), MAIN_METHODS);
    }
    
    /**
     * TrivialJar9 accesses a field on a param (a POJO) to a method.
     * 
     * @throws IllegalArgumentException
     * @throws CallGraphBuilderCancelException
     * @throws IOException
     * @throws ClassHierarchyException
     */
    @Test
    public final void test_flowFromInputPOJOParameter() 
            throws IllegalArgumentException, CallGraphBuilderCancelException,
            IOException, ClassHierarchyException {

    	String appJar = TEST_DATA_DIR + File.separator + "trivialJar9-1.0-SNAPSHOT.jar";
        runOnJar(appJar, new TestSpecs(), MAIN_METHODS);
    }
    
    /**
     * TrivialJar5 constructs an Integer object via new to test a dataflow problem.
     * 
     * 
     * @throws IllegalArgumentException
     * @throws CallGraphBuilderCancelException
     * @throws IOException
     * @throws ClassHierarchyException
     */
    @Test
    public final void test_flowViaNewInteger() 
            throws IllegalArgumentException, CallGraphBuilderCancelException,
            IOException, ClassHierarchyException {

        String appJar = TEST_DATA_DIR + File.separator + "trivialJar5-1.0-SNAPSHOT.jar";
        runOnJar(appJar, new TestSpecs(), MAIN_METHODS);
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
        runOnJar(appJar, new TestSpecs(), MAIN_METHODS);
    }
    
    @Ignore("Runs too long")
    @Test
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
        }, MAIN_METHODS);
    }
    
    /**
     * Test that loading a broken summary causes tests to fail.
     * 
     * @throws IllegalArgumentException
     * @throws CallGraphBuilderCancelException
     * @throws IOException
     * @throws ClassHierarchyException
     */
    @Test(expected=AssertionError.class)
    public final void test_brokensummaryBreaksDataFlow() 
            throws IllegalArgumentException, CallGraphBuilderCancelException,
            IOException, ClassHierarchyException {
        
        String appJar = "data/testdata/trivialJar1-1.0-SNAPSHOT.jar";

        checkSummaryProperty(appJar, new TestSpecs(),
        		"data/testdata/brokenSummary.xml", MAIN_METHODS);
    }
    
    private void runOnJar(String appJar, ISpecs specs, 
    		Predicate<IMethod> interestingMethod) 
    				throws IOException,
            ClassHierarchyException, CallGraphBuilderCancelException {
        String summary = summarize(appJar, interestingMethod);
        
        String contents = readFile(summary);
        System.out.println("-----     Summary File: -------");
        System.out.println(contents);
        System.out.println("-----   End Summary File -------");
        Assert.assertTrue("contents not long enough.", 80 <= contents.length());
        
        checkSummaryProperty(appJar, specs, summary, interestingMethod);
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
    private void checkSummaryProperty(String jarFile, ISpecs specs, 
    		String summaryFile, 
    		Predicate<IMethod> interestingEntrypoint) 
            throws ClassHierarchyException, CallGraphBuilderCancelException, IOException {
    	
    	long startTime = System.currentTimeMillis();
    	
        Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>>
          directResults = runDFAnalysis(jarFile, specs, WALA_NATIVES_XML, interestingEntrypoint);
        
        long directRunTime = System.currentTimeMillis() - startTime;
        
        
        System.out.println(" ----------------------------------------  ");
        System.out.println(" ---  DIRECT RESULTS DONE             ---  ");
        System.out.println(" ----------------------------------------  ");
        
        startTime = System.currentTimeMillis();
        Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>>
          summarizedResults = runDFAnalysis(jarFile, specs, summaryFile, interestingEntrypoint);
        
        long summaryRunTime = System.currentTimeMillis() - startTime;
        
        System.out.println("Direct runtime: "+directRunTime+" Summary runtime: "+summaryRunTime);
        System.out.println("Speedup of: "+ (directRunTime - summaryRunTime) + " ms");
        
        Assert.assertNotSame("No flows found in direct results.", 0, directResults.size());
        System.out.println("Actual Flows: \n"+flowMapToString(directResults));
        
        Assert.assertNotSame("No flows found in summarized results.", 0, summarizedResults.size());
        System.out.println("Summary Flows: \n"+flowMapToString(summarizedResults));
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
    private String summarize(String jarFile, Predicate<IMethod> interestingEntrypoint) 
        throws IOException, ClassHierarchyException, IllegalArgumentException, 
               CallGraphBuilderCancelException {
        
        @SuppressWarnings("unchecked")
        MethodAnalysis<IExplodedBasicBlock> methodAnalysis =
                // don't summarize the interesting entry points: 
                //new MethodAnalysis<IExplodedBasicBlock>(interestingEntrypoint.not());
        		new MethodAnalysis<IExplodedBasicBlock>(Predicate.TRUE);

        AnalysisScope scope = 
                DexAnalysisScopeReader.makeAndroidBinaryAnalysisScope(jarFile, 
                   new File("conf/Java60RegressionExclusions.txt"));
        ClassHierarchy cha = ClassHierarchy.make(scope);
        
        Collection<Entrypoint> entrypoints =
        		getEntrypoints(scope, cha, interestingEntrypoint);
        
        Assert.assertNotSame("No entry points found.", 0, entrypoints.size());
        
        AnalysisOptions options = new AnalysisOptions(scope, entrypoints);
        CallGraphBuilder builder = makeCallgraph(scope, cha, options, WALA_NATIVES_XML);

        
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

	private CallGraphBuilder makeCallgraph(AnalysisScope scope,
			ClassHierarchy cha, AnalysisOptions options, String methodSummariesFile) 
					throws FileNotFoundException {
		InputStream summaryStream = new FileInputStream(methodSummariesFile);
        CallGraphBuilder builder = 
                AndroidAppLoader.makeZeroCFABuilder(
                        options, new AnalysisCache(), cha, scope, null, null, 
                        summaryStream, null);
        
//        CallGraphBuilder builder = 
//                AndroidAppLoader.makeVanillaZeroOneCFABuilder(
//                        options, new AnalysisCache(), cha, scope, null, null, 
//                        methodSummariesFile, null);
        
		return builder;
	}

    private Collection<Entrypoint> getEntrypoints(AnalysisScope scope, 
    		ClassHierarchy cha,
    		Predicate<IMethod> isInteresting) {
        List<Entrypoint> entrypoints = new ArrayList<Entrypoint>();
		for (IClass iClass : cha) {
			for (IMethod iMethod : iClass.getAllMethods()) {
				if (isInteresting.test(iMethod)) {
					if (LoaderUtils.fromLoader(iMethod,
							ClassLoaderReference.Application)) {
						entrypoints.add(new DefaultEntrypoint(iMethod, cha));
					}
				}
			}
		}
        return entrypoints;
        // return Util.makeMainEntrypoints(scope, cha);
    }
    
    private
    Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>> 
    runDFAnalysis(String appJar, 
    		      ISpecs specs,
    		      String methodSummariesFile,
    		      Predicate<IMethod> interestingEntrypoint) 
        throws IOException, ClassHierarchyException, 
               CallGraphBuilderCancelException {

        @SuppressWarnings("unchecked")
		MethodAnalysis<IExplodedBasicBlock> methodAnalysis = null;
        		// don't summarize the interesting entry points. 
        		//new MethodAnalysis<IExplodedBasicBlock>(interestingEntrypoint.not());
				//new MethodAnalysis<IExplodedBasicBlock>(Predicate.TRUE);
        
        AnalysisScope scope = 
                DexAnalysisScopeReader.makeAndroidBinaryAnalysisScope(appJar, 
                   new File("conf/Java60RegressionExclusions.txt"));
        ClassHierarchy cha = ClassHierarchy.make(scope);
        
        Collection<Entrypoint> entrypoints = 
        		getEntrypoints(scope, cha, interestingEntrypoint);
        
        Assert.assertNotSame("No entry points found.", 0, entrypoints.size());
        
        AnalysisOptions options = new AnalysisOptions(scope, entrypoints);
        CallGraphBuilder builder = 
        		makeCallgraph(scope, cha, options, methodSummariesFile);

        
        CallGraph cg = builder.makeCallGraph(options, null);

        
        ISupergraph<BasicBlockInContext<IExplodedBasicBlock>, CGNode> sg = 
                ICFGSupergraph.make(cg, builder.getAnalysisCache());
        PointerAnalysis pa = builder.getPointerAnalysis();
        
        Map<BasicBlockInContext<IExplodedBasicBlock>, 
            Map<FlowType<IExplodedBasicBlock>, Set<CodeElement>>> initialTaints = 
              InflowAnalysis.analyze(cg, cha, sg, pa, new HashMap<InstanceKey, String>(), specs);
                   
        System.out.println("  InitialTaints count: "+initialTaints.size());
        
        IFDSTaintDomain<IExplodedBasicBlock> domain = new IFDSTaintDomain<IExplodedBasicBlock>();
        TabulationResult<BasicBlockInContext<IExplodedBasicBlock>, CGNode, DomainElement> 
          flowResult = FlowAnalysis.analyze(sg, cg, pa, initialTaints, domain, methodAnalysis);
        
        Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>>
          permissionOutflow = OutflowAnalysis.analyze(cg, cha, sg, pa, flowResult, domain, specs);
        
        return permissionOutflow;
    }
    
    public static String flowMapToString(Map<FlowType<IExplodedBasicBlock>, 
    		                             Set<FlowType<IExplodedBasicBlock>>> flowMap) {
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

    public static String readFile( String path ) throws IOException {
        FileInputStream stream = new FileInputStream(new File(path));
        try {
          FileChannel fc = stream.getChannel();
          MappedByteBuffer bb = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
          /* Instead of using default, pass in a decoder. */
          return Charset.defaultCharset().decode(bb).toString();
        }
        finally {
          stream.close();
        }
    }
}
