package org.scandroid.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ibm.wala.classLoader.DexIRFactory;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.dataflow.IFDS.ICFGSupergraph;
import com.ibm.wala.dataflow.IFDS.ISupergraph;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.callgraph.impl.PartialCallGraph;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.callgraph.propagation.ReceiverTypeContextSelector;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ssa.IRFactory;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSACFG.BasicBlock;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.util.Predicate;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.GraphSlicer;
import com.ibm.wala.util.warnings.Warning;
import com.ibm.wala.util.warnings.Warnings;

/**
 * @author acfoltzer
 * 
 *         Represents an analysis context after the call graph, pointer
 *         analysis, and supergraphs have been generated. This is separated from
 *         AndroidAnalysisContext since these depend on the entrypoints for
 *         analysis in a way that is not likely reusable across all analyses of
 *         a particular classpath
 */
public class CGAnalysisContext<E extends ISSABasicBlock> {
	private static final Logger logger = LoggerFactory
			.getLogger(CGAnalysisContext.class);

	public final AndroidAnalysisContext analysisContext;

	private List<Entrypoint> entrypoints;
	public CallGraph cg;
	public PointerAnalysis pa;
	public ISupergraph<BasicBlockInContext<E>, CGNode> graph;
	public Graph<CGNode> partialGraph;
	public Graph<CGNode> oneLevelGraph;
	public Graph<CGNode> systemToApkGraph;

	public CGAnalysisContext(AndroidAnalysisContext analysisContext,
			IEntryPointSpecifier specifier) throws IOException {
		this(analysisContext, specifier, new ArrayList<InputStream>());
	}

	public CGAnalysisContext(AndroidAnalysisContext analysisContext,
			IEntryPointSpecifier specifier,
			Collection<InputStream> extraSummaries) throws IOException {
		this.analysisContext = analysisContext;
		final AnalysisScope scope = analysisContext.getScope();
		final ClassHierarchy cha = analysisContext.getClassHierarchy();
		final ISCanDroidOptions options = analysisContext.getOptions();

		entrypoints = specifier.specify(analysisContext);
		AnalysisOptions analysisOptions = new AnalysisOptions(scope,
				entrypoints);
		for (Entrypoint e : entrypoints) {
			logger.debug("Entrypoint: " + e);
		}

		analysisOptions.setEntrypoints(entrypoints);
		analysisOptions.setReflectionOptions(options.getReflectionOptions());

		AnalysisCache cache = new AnalysisCache(
				(IRFactory<IMethod>) new DexIRFactory());

		SSAPropagationCallGraphBuilder cgb;

		if (null != options.getSummariesURI() ) {
			extraSummaries.add(new FileInputStream(new File(options.getSummariesURI())));
		}
		
		cgb = AndroidAnalysisContext.makeZeroCFABuilder(analysisOptions, cache,
				cha, scope, new ReceiverTypeContextSelector(), null,
				extraSummaries, null);

		if (analysisContext.getOptions().cgBuilderWarnings()) {
			// CallGraphBuilder construction warnings
			for (Iterator<Warning> wi = Warnings.iterator(); wi.hasNext();) {
				Warning w = wi.next();
				logger.warn(w.getMsg());
			}
		}
		Warnings.clear();

		logger.info("*************************");
		logger.info("* Building Call Graph   *");
		logger.info("*************************");

		boolean graphBuilt = true;
		try {
			cg = cgb.makeCallGraph(cgb.getOptions());
		} catch (Exception e) {
			graphBuilt = false;
			if (!options.testCGBuilder()) {
				throw new RuntimeException(e);
			} else {
				e.printStackTrace();
			}
		}

		if (options.testCGBuilder()) {
			// TODO: this is too specialized for cmd-line apps
			int status = graphBuilt ? 0 : 1;
			System.exit(status);
		}

		// makeCallGraph warnings
		for (Iterator<Warning> wi = Warnings.iterator(); wi.hasNext();) {
			Warning w = wi.next();
			logger.warn(w.getMsg());
		}
		Warnings.clear();

		pa = cgb.getPointerAnalysis();

		// TODO: prune out a lot more stuff
		partialGraph = GraphSlicer.prune(cg, new Predicate<CGNode>() {
			@Override
			// CallGraph composed of APK nodes
			public boolean test(CGNode node) {
				return LoaderUtils.fromLoader(node,
						ClassLoaderReference.Application)
						|| node.getMethod().isSynthetic();
			}
		});

		Collection<CGNode> nodes = new HashSet<CGNode>();

		for (Iterator<CGNode> nIter = partialGraph.iterator(); nIter.hasNext();) {
			nodes.add(nIter.next());
		}

		CallGraph pcg = PartialCallGraph.make(cg, cg.getEntrypointNodes(),
				nodes);

		if (options.includeLibrary())
			graph = (ISupergraph) ICFGSupergraph.make(cg, cache);
		else
			graph = (ISupergraph) ICFGSupergraph.make(pcg, cache);

		oneLevelGraph = GraphSlicer.prune(cg, new Predicate<CGNode>() {
			@Override
			public boolean test(CGNode node) {
				// Node in APK
				if (LoaderUtils.fromLoader(node,
						ClassLoaderReference.Application)) {
					return true;
				} else {
					Iterator<CGNode> n = cg.getPredNodes(node);
					while (n.hasNext()) {
						// Primordial node has a successor in APK
						if (LoaderUtils.fromLoader(n.next(),
								ClassLoaderReference.Application))
							return true;
					}
					n = cg.getSuccNodes(node);
					while (n.hasNext()) {
						// Primordial node has a predecessor in APK
						if (LoaderUtils.fromLoader(n.next(),
								ClassLoaderReference.Application))
							return true;
					}
					// Primordial node with no direct successors or predecessors
					// to APK code
					return false;
				}
			}
		});

		systemToApkGraph = GraphSlicer.prune(cg, new Predicate<CGNode>() {
			@Override
			public boolean test(CGNode node) {

				if (LoaderUtils.fromLoader(node,
						ClassLoaderReference.Primordial)) {
					Iterator<CGNode> succs = cg.getSuccNodes(node);
					while (succs.hasNext()) {
						CGNode n = succs.next();

						if (LoaderUtils.fromLoader(n,
								ClassLoaderReference.Application)) {
							return true;
						}
					}
					// Primordial method, with no link to APK code:
					return false;
				} else if (LoaderUtils.fromLoader(node,
						ClassLoaderReference.Application)) {
					// see if this is an APK method that was
					// invoked by a Primordial method:
					Iterator<CGNode> preds = cg.getPredNodes(node);
					while (preds.hasNext()) {
						CGNode n = preds.next();

						if (LoaderUtils.fromLoader(n,
								ClassLoaderReference.Primordial)) {
							return true;
						}
					}
					// APK code, no link to Primordial:
					return false;
				}

				// who knows, not interesting:
				return false;
			}
		});

		if (options.pdfCG())
			GraphUtil.makeCG(this);
		if (options.pdfPartialCG())
			GraphUtil.makePCG(this);
		if (options.pdfOneLevelCG())
			GraphUtil.makeOneLCG(this);
		if (options.systemToApkCG())
			GraphUtil.makeSystemToAPKCG(this);

		if (options.stdoutCG()) {
			for (Iterator<CGNode> nodeI = cg.iterator(); nodeI.hasNext();) {
				CGNode node = nodeI.next();

				logger.debug("CGNode: " + node);
				for (Iterator<CGNode> succI = cg.getSuccNodes(node); succI
						.hasNext();) {
					logger.debug("\tSuccCGNode: "
							+ succI.next().getMethod().getSignature());
				}
			}
		}
		for (Iterator<CGNode> nodeI = cg.iterator(); nodeI.hasNext();) {
			CGNode node = nodeI.next();
			if (node.getMethod().isSynthetic()) {
				logger.trace("Synthetic Method: {}", node.getMethod()
						.getSignature());
				logger.trace("{}", node.getIR().getControlFlowGraph()
						.toString());
				SSACFG ssaCFG = node.getIR().getControlFlowGraph();
				int totalBlocks = ssaCFG.getNumberOfNodes();
				for (int i = 0; i < totalBlocks; i++) {
					logger.trace("BLOCK #{}", i);
					BasicBlock bb = ssaCFG.getBasicBlock(i);

					for (SSAInstruction ssaI : bb.getAllInstructions()) {
						logger.trace("\tInstruction: {}", ssaI);
					}
				}
			}
		}
	}

	public ISCanDroidOptions getOptions() {
		return analysisContext.getOptions();
	}

	public ClassHierarchy getClassHierarchy() {
		return analysisContext.getClassHierarchy();
	}

	public AnalysisScope getScope() {
		return analysisContext.getScope();
	}

	public List<Entrypoint> getEntrypoints() {
		return entrypoints;
	}

	public CGNode nodeForMethod(IMethod method) {
		return cg.getNode(method, Everywhere.EVERYWHERE);
	}
}
