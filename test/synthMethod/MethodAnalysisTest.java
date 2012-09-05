package synthMethod;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

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
    
    @Test
    public final void test() 
            throws IllegalArgumentException, CallGraphBuilderCancelException,
            IOException, ClassHierarchyException {
        
        String appJar = "/home/creswick/development/fuse/dev/trivialJar1/target/trivialJar1-1.0-SNAPSHOT.jar";

        runDFAnalysis(appJar, "");
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
     * @throws ClassHierarchyException 
     */
    private void checkSummaryProperty(String jarFile, String summaryFile) 
            throws ClassHierarchyException, CallGraphBuilderCancelException, IOException {
        Map<FlowType, Set<FlowType>> directResults = 
                runDFAnalysis(jarFile, WALA_NATIVES_XML);
        
        Map<FlowType, Set<FlowType>> summarizedResults = 
                runDFAnalysis(jarFile, summaryFile);
        
        Assert.assertTrue("Results differed with summaries", 
                directResults.equals(summarizedResults));
    }
    
    /**
     * Generate summaries for all the entry points in a jar file, write the 
     * summaries to a file, and return the file name.
     * 
     * @param jarFile
     * @return The filename that the summaries were written to.
     */
    private String summarize(String jarFile) {
        
        /*
        for (Iterator<CGNode> itr = nodes.iterator(); itr.hasNext();) {
            CGNode cgNode = (CGNode) itr.next();
            
            BasicBlockInContext<IExplodedBasicBlock>[] entriesForProcedure =
                    sg.getEntriesForProcedure(cgNode);
            for (int i = 0; i < entriesForProcedure.length; i++) {
                methodAnalysis.analyze(sg, 
                        builder.getPointerAnalysis(),
                        entriesForProcedure[i]);
            };
        }
        
        XMLMethodSummaryWriter.createXML(methodAnalysis); */
        
        return null;
    }
    
    private
    Map<FlowType, Set<FlowType>> 
    runDFAnalysis(String appJar, String methodSummariesFile) 
        throws IOException, ClassHierarchyException, 
               CallGraphBuilderCancelException {
        
        MethodAnalysis<IExplodedBasicBlock> methodAnalysis =
                new MethodAnalysis<IExplodedBasicBlock>();
        AnalysisScope scope = 
                DexAnalysisScopeReader.makeAndroidBinaryAnalysisScope(appJar, 
                   new File("conf/Java60RegressionExclusions.txt"));
        ClassHierarchy cha = ClassHierarchy.make(scope);
        
        List<Entrypoint> entrypoints = new ArrayList<Entrypoint>();
        for (IClass iClass : cha) {
            for (Iterator<IMethod> itr = iClass.getAllMethods().iterator(); itr.hasNext();) {
                IMethod iMethod = itr.next();
                
                if ( LoaderUtils.fromLoader(iMethod, ClassLoaderReference.Application) ) {
                    entrypoints.add(new DefaultEntrypoint(iMethod, cha));
                }
            }
        }
        
        AnalysisOptions options = new AnalysisOptions(scope, entrypoints);
        CallGraphBuilder builder = 
           AndroidAppLoader.makeVanillaZeroOneCFABuilder(
                   options, new AnalysisCache(), cha, scope, null, null, methodSummariesFile);
        
        CallGraph cg = builder.makeCallGraph(options, null);

        ISupergraph<BasicBlockInContext<IExplodedBasicBlock>, CGNode> sg = 
                ICFGSupergraph.make(cg, builder.getAnalysisCache());
        PointerAnalysis pa = builder.getPointerAnalysis();
        
        System.out.println("Running inflow analysis.");
        Map<BasicBlockInContext<IExplodedBasicBlock>, 
            Map<FlowType, Set<CodeElement>>> initialTaints = 
              InflowAnalysis.analyze(cg, cha, sg, pa, new HashMap<InstanceKey, String>());
                   
        System.out.println("Running flow analysis.");
        IFDSTaintDomain<IExplodedBasicBlock> domain = new IFDSTaintDomain<IExplodedBasicBlock>();
        TabulationResult<BasicBlockInContext<IExplodedBasicBlock>, CGNode, DomainElement> 
          flowResult = FlowAnalysis.analyze(sg, cg, pa, initialTaints, domain, methodAnalysis);

        System.out.println("Running outflow analysis.");
        Map<FlowType, Set<FlowType>> permissionOutflow = 
                OutflowAnalysis.analyze(cg, cha, sg, pa, flowResult, domain);
        
        return permissionOutflow;
    }
    

}
