package synthMethod;

import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.junit.Test;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.dataflow.IFDS.ICFGSupergraph;
import com.ibm.wala.dataflow.IFDS.ISupergraph;
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
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.util.config.AnalysisScopeReader;

public class MethodAnalysisTest {

    MethodAnalysis methodAnalysis = null;
    
    @Test
    public final void test() 
            throws IllegalArgumentException, CallGraphBuilderCancelException,
            IOException, ClassHierarchyException {
        String appJar = "/home/creswick/development/fuse/dev/trivialJar1/target/trivialJar1-1.0-SNAPSHOT.jar";
        AnalysisScope scope = DexAnalysisScopeReader.makeAndroidBinaryAnalysisScope(appJar, 
                new File("conf/Java60RegressionExclusions.txt"));
        ClassHierarchy cha = ClassHierarchy.make(scope);
        
        List<Entrypoint> entrypoints = new ArrayList<Entrypoint>();
        for (IClass iClass : cha) {
            for (Iterator<IMethod> itr = iClass.getAllMethods().iterator(); itr.hasNext();) {
                IMethod iMethod = (IMethod) itr.next();
                entrypoints.add(new DefaultEntrypoint(iMethod, cha));
            }
        }
        
        AnalysisOptions options = new AnalysisOptions(scope, entrypoints);

        // //
        // build the call graph
        // //
        CallGraphBuilder builder = 
                Util.makeVanillaZeroOneCFABuilder(options, new AnalysisCache(), cha, scope);
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
                        entriesForProcedure[i]);
            };
        }
    }
}
