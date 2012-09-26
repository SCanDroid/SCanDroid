package org.scandroid;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.log4j.BasicConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spec.ISpecs;
import synthMethod.XMLSummaryWriter;
import util.AndroidAppLoader;
import util.ThrowingSSAInstructionVisitor;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
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
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.LocalPointerKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.summaries.MethodSummary;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.collections.Filter;
import com.ibm.wala.util.graph.traverse.DFSPathFinder;
import com.ibm.wala.util.intset.OrdinalSet;
import com.ibm.wala.util.strings.StringStuff;

import domain.CodeElement;
import domain.DomainElement;
import domain.IFDSTaintDomain;
import flow.FlowAnalysis;
import flow.InflowAnalysis;
import flow.OutflowAnalysis;
import flow.types.FieldFlow;
import flow.types.FlowType;
import flow.types.FlowType.FlowTypeVisitor;
import flow.types.IKFlow;
import flow.types.ParameterFlow;
import flow.types.ReturnFlow;

public class Summarizer<E extends ISSABasicBlock> {
	public static final Logger logger = LoggerFactory
			.getLogger(Summarizer.class);
	public static final String WALA_NATIVES_XML = "wala/wala-src/com.ibm.wala.core/dat/natives.xml";

	/**
	 * @param args
	 * @throws IOException
	 * @throws CallGraphBuilderCancelException
	 * @throws ClassHierarchyException
	 * @throws ParserConfigurationException
	 */
	public static void main(String[] args) throws ClassHierarchyException,
			CallGraphBuilderCancelException, IOException,
			ParserConfigurationException {

		BasicConfigurator.configure();
		if (args.length < 2) {
			logger.error("Usage: Summarizer <jarfile> <methoddescriptor>");
			logger.error("   methoddescriptor -- a specification of a java method, formatted as:");
			logger.error("                       some.package.Clasas(Ljava/lang/String;I)Ljava/lang/String;");
			System.exit(1);
		}

		String appJar = args[0];
		String methoddescriptor = args[1];
		
		Summarizer<IExplodedBasicBlock> s = new Summarizer<IExplodedBasicBlock>(
				appJar, methoddescriptor);

		System.out.println(s.summarize());
	}

	private final String methodDescriptor;
	private ISupergraph<BasicBlockInContext<IExplodedBasicBlock>, CGNode> graph;
	private CallGraph cg;
	private final PointerAnalysis pa;
	private MethodReference methodRef;
	private final AnalysisScope scope;
	private final ClassHierarchy cha;
	private CallGraphBuilder builder;

	public Summarizer(String appJar, String methoddescriptor)
			throws IOException, ClassHierarchyException,
			IllegalArgumentException, CallGraphBuilderCancelException {
		this.methodDescriptor = methoddescriptor;
		this.methodRef = StringStuff.makeMethodReference(methodDescriptor);
		this.scope = DexAnalysisScopeReader.makeAndroidBinaryAnalysisScope(
				appJar, new File("conf/Java60RegressionExclusions.txt"));
		this.cha = ClassHierarchy.make(scope);

		Iterable<Entrypoint> entrypoints = ImmutableList
				.<Entrypoint> of(new DefaultEntrypoint(methodRef, cha));
		AnalysisOptions options = new AnalysisOptions(scope, entrypoints);
		builder = makeCallgraph(scope, cha, options, WALA_NATIVES_XML);
		this.cg = builder.makeCallGraph(options, null);
		this.pa = builder.getPointerAnalysis();

		this.graph = ICFGSupergraph.make(cg, builder.getAnalysisCache());
	}

	private String summarize() throws ClassHierarchyException,
			CallGraphBuilderCancelException, IOException,
			ParserConfigurationException {

		Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>> dfAnalysis = runDFAnalysis();
		logger.debug(dfAnalysis.toString());

		MethodSummary summary = new MethodSummary(methodRef);

		Collection<IMethod> entryMethods = cha.getPossibleTargets(methodRef);
		if (entryMethods.size() != 1) {
			logger.error("More than one imethod found for: " + methodRef);
		}

		IMethod imethod = entryMethods.iterator().next();

		List<SSAInstruction> instructions = compileFlowMap(imethod, dfAnalysis);

		for (SSAInstruction inst : instructions) {
			summary.addStatement(inst);
		}

		XMLSummaryWriter writer = new XMLSummaryWriter();
		writer.add(summary);

		return writer.serialize();
	}

	private Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>> runDFAnalysis()
			throws IOException, ClassHierarchyException,
			CallGraphBuilderCancelException {

		ISpecs specs = new MethodSummarySpecs(methodRef);

		Map<BasicBlockInContext<IExplodedBasicBlock>, Map<FlowType<IExplodedBasicBlock>, Set<CodeElement>>> initialTaints = InflowAnalysis
				.analyze(cg, cha, graph, pa,
						new HashMap<InstanceKey, String>(), specs);

		System.out.println("  InitialTaints count: " + initialTaints.size());

		IFDSTaintDomain<IExplodedBasicBlock> domain = new IFDSTaintDomain<IExplodedBasicBlock>();
		TabulationResult<BasicBlockInContext<IExplodedBasicBlock>, CGNode, DomainElement> flowResult = FlowAnalysis
				.analyze(graph, cg, pa, initialTaints, domain, null);

		Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>> permissionOutflow = OutflowAnalysis
				.analyze(cg, cha, graph, pa, flowResult, domain, specs);

		return permissionOutflow;
	}

	// private void runDFAnalysis(String appJar) throws IOException,
	// ClassHierarchyException, CallGraphBuilderCancelException {
	//
	// AnalysisScope scope = DexAnalysisScopeReader
	// .makeAndroidBinaryAnalysisScope(appJar, new File(
	// "conf/Java60RegressionExclusions.txt"));
	// ClassHierarchy cha = ClassHierarchy.make(scope);
	//
	// MethodReference methodRef = StringStuff
	// .makeMethodReference(methodDescriptor);
	// Iterable<Entrypoint> entrypoints = ImmutableList
	// .<Entrypoint> of(new DefaultEntrypoint(methodRef, cha));
	//
	// AnalysisOptions options = new AnalysisOptions(scope, entrypoints);
	// CallGraphBuilder builder = makeCallgraph(scope, cha, options, null);
	//
	// CallGraph cg = builder.makeCallGraph(options, null);
	//
	// graph = null; // ICFGSupergraph.make(cg, builder.getAnalysisCache());
	// PointerAnalysis pa = builder.getPointerAnalysis();
	//
	//
	// ISpecs methodSummarySpecs = new MethodSummarySpecs(methodRef);
	//
	// // Map<BasicBlockInContext<IExplodedBasicBlock>,
	// // Map<FlowType<IExplodedBasicBlock>, Set<CodeElement>>> initialTaints =
	// // InflowAnalysis.analyze(cg, cha, sg, pa, new HashMap<InstanceKey,
	// // String>(), specs);
	// IFDSTaintDomain<E> domain = new IFDSTaintDomain<E>();
	//
	// // Collection<IMethod> entryMethods = cha.getPossibleTargets(methodRef);
	// // final IMethod entryMethod;
	// // if (1 != entryMethods.size()) {
	// // System.err.println("Too many IMethods for method reference "
	// // + "(or none at all).  found: " + entryMethods.size());
	// // throw new IllegalArgumentException();
	// // } else {
	// // entryMethod = entryMethods.iterator().next();
	// // }
	// //
	// // MethodAnalysis<E> methodAnalysis = new MethodAnalysis<E>(
	// // new Predicate<IMethod>() {
	// // @Override
	// // public boolean test(IMethod im) {
	// // return im.equals(entryMethod);
	// // }
	// // });
	// // methodAnalysis.analyze(graph, pa, null,
	// // graph.getEntriesForProcedure(cg
	// // .getNode(entryMethod, Everywhere.EVERYWHERE))[0]);
	// //
	// // System.out.println(methodAnalysis.newSummaries);
	//
	// //
	// // Map<BasicBlockInContext<IExplodedBasicBlock>,
	// // Map<FlowType<IExplodedBasicBlock>, Set<CodeElement>>>
	// // initialTaints = setUpTaints(sg, cg, pa, domain, entryMethod);
	// //
	// // TabulationResult<BasicBlockInContext<IExplodedBasicBlock>, CGNode,
	// // DomainElement>
	// // flowResult = FlowAnalysis.analyze(sg, cg, pa, initialTaints, domain,
	// // null);
	// //
	// // // Map<FlowType<IExplodedBasicBlock>,
	// // // Set<FlowType<IExplodedBasicBlock>>>
	// // // permissionOutflow = OutflowAnalysis.analyze(cg, cha, sg, pa,
	// // // flowResult, domain, specs);
	// // System.out.println(flowResult);
	// //
	// // return makeSummary(flowResult);
	// }

	private CallGraphBuilder makeCallgraph(AnalysisScope scope,
			ClassHierarchy cha, AnalysisOptions options,
			String methodSummariesFile) throws FileNotFoundException {

		CallGraphBuilder builder = AndroidAppLoader.makeZeroCFABuilder(options,
				new AnalysisCache(), cha, scope, null, null, null, null);

		return builder;
	}

	/**
	 * Eventually, we'd like these pointer keys to encompass the entire
	 * environment (such as static fields) in scope for this method. For now,
	 * though, parameters suffice.
	 * 
	 * @param method
	 * @return
	 */
	public Set<PointerKey> getInputPointerKeys(IMethod method) {
		CGNode node = nodeForMethod(method);
		Set<PointerKey> pkSet = Sets.newHashSet();
		for (int p : node.getIR().getParameterValueNumbers()) {
			pkSet.add(new LocalPointerKey(node, p));
		}
		return pkSet;
	}

	public List<PointerKey> getAccessPath(Set<PointerKey> pkSet,
			final PointerKey pk) {
		List<PointerKey> path = Lists.newArrayList();

		DFSPathFinder<Object> finder = new DFSPathFinder<Object>(
				pa.getHeapGraph(), pkSet.iterator(), new Filter<Object>() {
					public boolean accepts(Object o) {
						return (pk.equals(o));
					}
				});
		List<Object> result = finder.find();
		if (result == null)
			return null;
		for (Object step : result) {
			if (step instanceof PointerKey) {
				path.add((PointerKey) step);
			}
		}
		return path;
	}

	public List<SSAInstruction> compileFlowMap(
			IMethod method,
			Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>> flowMap) {
		final List<SSAInstruction> insts = Lists.newArrayList();
		// keep track of which SSA values have already been added to the result
		// list, and so can be referenced by subsequent instructions
		final BitSet refInScope = new BitSet();
		for (Entry<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>> entry : flowMap
				.entrySet()) {
			insts.addAll(compileFlowType(method, entry.getKey(), refInScope, -1));
			for (FlowType<IExplodedBasicBlock> flow : entry.getValue()) {
				insts.addAll(compileFlowType(method, flow, refInScope, -1));
			}
		}
		logger.debug("compiled flowMap: " + insts.toString());
		return insts;

	}

	private List<SSAInstruction> compileFlowType(final IMethod method,
			final FlowType<IExplodedBasicBlock> ft, final BitSet refInScope) {
		return compileFlowType(method, ft, refInScope, -1);
	}

	private List<SSAInstruction> compileFlowType(final IMethod method,
			final FlowType<IExplodedBasicBlock> ft, final BitSet refInScope,
			int lhsVal) {
		// what's the largest SSA value that refers to a parameter?
		final int maxParam = method.getNumberOfParameters();
		// set the implicit values for parameters
		refInScope.set(1, maxParam + 1);

		final CGNode node = nodeForMethod(method);
		final DefUse du = node.getDU();

		final List<SSAInstruction> insts = Lists.newArrayList();
		// in case order matters, add any return statements to this list, to be
		// combined at the end
		final List<SSAInstruction> returns = Lists.newArrayList();
		ft.visit(new FlowType.FlowTypeVisitor<IExplodedBasicBlock, Void>() {

			final class PathWalker extends ThrowingSSAInstructionVisitor {

				public PathWalker() {
					super(new IllegalArgumentException(
							"unhandled SSAInstruction"));
				}

				@Override
				public void visitGet(SSAGetInstruction instruction) {
					// if this val is already in scope, do nothing
					if (refInScope.get(instruction.getDef()))
						return;

					int ref = instruction.getRef();
					if (ref != -1 && !refInScope.get(ref)) {
						// ref is not in scope yet, so find the SSA
						// instruction that brings it into scope
						SSAInstruction refInst = du.getDef(ref);
						refInst.visit(this);
					}
					// postcondition: ref is now in scope
					assert ref == -1 || refInScope.get(ref);

					insts.add(instruction);
					refInScope.set(instruction.getDef());
				}

				@Override
				public void visitPut(SSAPutInstruction instruction) {
					int val = instruction.getVal();
					if (!refInScope.get(val)) {
						// if the RHS of the assignment is not in scope, recur
						SSAInstruction valInst = du.getDef(val);
						valInst.visit(this);
					}
					// postcondition: val is now in scope
					assert refInScope.get(val);

					int ref = instruction.getRef();
					if (ref != -1 && !refInScope.get(ref)) {
						// ref is not in scope yet, so find the SSA
						// instruction that brings it into scope
						SSAInstruction refInst = du.getDef(ref);
						refInst.visit(this);
					}
					// postcondition: ref is now in scope
					assert ref == -1 || refInScope.get(ref);

					insts.add(instruction);
				}

				@Override
				public void visitInvoke(SSAInvokeInstruction instruction) {
					// if this val is already in scope, do nothing
					if (refInScope.get(instruction.getDef()))
						return;

					// get all the param refvals
					int params[] = new int[instruction.getNumberOfParameters()];
					for (int paramIndex = 0; paramIndex < params.length; paramIndex++) {
						params[paramIndex] = instruction.getUse(paramIndex);
					}

					// make sure all params are in scope
					for (int param : params) {
						if (!refInScope.get(param)) {
							// ref is not in scope yet, so find the SSA
							// instruction that brings it into scope
							SSAInstruction paramInst = du.getDef(param);
							paramInst.visit(this);
						}
						// postcondition: param is now in scope
						assert refInScope.get(param);
					}
					// postcondition: all params are now in scope

					insts.add(instruction);
					// only set refInScope if non-void:
					if (instruction.getNumberOfReturnValues() == 1) {
						refInScope.set(instruction.getReturnValue(0));
					}
				}

				@Override
				public void visitNew(SSANewInstruction instruction) {
					// if already in scope, do nothing
					int def = instruction.getDef();
					if (refInScope.get(def))
						return;

					// otherwise, just add the new instruction. Remember that
					// constructors are handled as separate <init> methods
					insts.add(instruction);
					refInScope.set(def);
				}

				@Override
				public void visitReturn(SSAReturnInstruction instruction) {
					// returns only have a single use (-1 if void return), so
					// walk that val if present and then add this instruction to
					// the return list
					int use = instruction.getUse(0);
					if (use != -1 && !refInScope.get(use)) {
						// use is not in scope yet
						SSAInstruction useInst = du.getDef(use);
						useInst.visit(this);
					}
					// postcondition: use is now in scope, if present
					assert (use == -1 || refInScope.get(use));
					returns.add(instruction);
				}

			}

			@Override
			public Void visitFieldFlow(FieldFlow<IExplodedBasicBlock> flow) {
				if (flow.getBlock().getLastInstructionIndex() != 0) {
					logger.warn("basic block with length other than 1: "
							+ flow.getBlock());
				}
				flow.getBlock().getLastInstruction().visit(new PathWalker());
				return null;
			}

			@Override
			public Void visitIKFlow(IKFlow<IExplodedBasicBlock> flow) {
				IllegalArgumentException e = new IllegalArgumentException(
						"shouldn't find any IKFlows");
				logger.error("exception compiling FlowType", e);
				throw e;
			}

			@Override
			public Void visitParameterFlow(
					ParameterFlow<IExplodedBasicBlock> flow) {
				// ParameterFlow can be used in two ways. Here we handle the way
				// that references a parameter of the current method, and do
				// nothing. The other way involves arguments to method
				// invocations, and AT says we shouldn't see any of those
				// currently.

				// This loop detects the first case, where the block associated
				// with the flow is equal to the entry block of the method
				boolean equal = false;
				for (BasicBlockInContext<IExplodedBasicBlock> entryBlock : graph
						.getEntriesForProcedure(node)) {
					equal = equal || flow.getBlock().equals(entryBlock);
				}
				if (!equal) {
					IllegalArgumentException e = new IllegalArgumentException(
							"shouldn't have any ParameterFlows for invoked arguments");
					logger.error("exception compiling FlowType", e);
				}
				return null;
			}

			@Override
			public Void visitReturnFlow(ReturnFlow<IExplodedBasicBlock> flow) {
				if (flow.getBlock().getLastInstructionIndex() != 0) {
					logger.warn("basic block with length other than 1: "
							+ flow.getBlock());
				}
				SSAInstruction inst = flow.getBlock().getLastInstruction();
				// TODO: SUPPOSEDLY Two cases here:
				// 1. source == true: block should be an invoke instruction
				// 2. source == false: block should be a return instruction
				// handle both by invoking the PathWalker to ensure all relevant
				// refs are in scope
				if (inst == null) {
					Iterator<BasicBlockInContext<IExplodedBasicBlock>> it = graph
							.getPredNodes(flow.getBlock());
					while (it.hasNext()) {
						BasicBlockInContext<IExplodedBasicBlock> realBlock = it
								.next();
						SSAInstruction realInst = realBlock
								.getLastInstruction();
						realInst.visit(new PathWalker());
					}
				} else {
					inst.visit(new PathWalker());
				}
				final PointerKey pkFromFlowType = getPKFromFlowType(method, flow);
				logger.debug("ReturnFlow PK: " + pkFromFlowType);
				logger.debug("Path from params: " + getAccessPath(getInputPointerKeys(method), pkFromFlowType));
				return null;
			}
		});
		insts.addAll(returns);
		return insts;
	}

	private PointerKey getPKFromFlowType(final IMethod method,
			FlowType<IExplodedBasicBlock> ft) {
		return ft.visit(new FlowTypeVisitor<IExplodedBasicBlock, PointerKey>() {
			final CGNode node = nodeForMethod(method);

			@Override
			public PointerKey visitFieldFlow(FieldFlow<IExplodedBasicBlock> flow) {
				int val = flow.getBlock().getLastInstruction().getUse(0);

				if (val == -1) {
					// static field access; easy
					return pa.getHeapModel().getPointerKeyForStaticField(
							flow.getField());
				}

				// first look up the PK of the reference
				PointerKey instancePK = pa.getHeapModel()
						.getPointerKeyForLocal(node, val);

				// then get IKs for this PK. under 0cfa, this should just be a
				// singleton
				OrdinalSet<InstanceKey> iks = pa.getPointsToSet(instancePK);
				Iterator<InstanceKey> ikIter = iks.iterator();
				InstanceKey instanceIK = ikIter.next();
				// if there are any other candidates, warn
				if (ikIter.hasNext()) {
					logger.warn("found multiple IKs for a PK");
				}
				return pa.getHeapModel().getPointerKeyForInstanceField(
						instanceIK, flow.getField());
			}

			@Override
			public PointerKey visitIKFlow(IKFlow<IExplodedBasicBlock> flow) {
				throw new IllegalArgumentException("IKFlows not implemented");
			}

			@Override
			public PointerKey visitParameterFlow(
					ParameterFlow<IExplodedBasicBlock> flow) {
				// ParameterFlow can be used in two ways. Here we handle the way
				// that references a parameter of the current method, and do
				// nothing. The other way involves arguments to method
				// invocations, and AT says we shouldn't see any of those
				// currently.

				// This loop detects the first case, where the block associated
				// with the flow is equal to the entry block of the method
				boolean equal = false;
				for (BasicBlockInContext<IExplodedBasicBlock> entryBlock : graph
						.getEntriesForProcedure(node)) {
					equal = equal || flow.getBlock().equals(entryBlock);
				}
				if (!equal) {
					IllegalArgumentException e = new IllegalArgumentException(
							"shouldn't have any ParameterFlows for invoked arguments");
					logger.error("exception compiling FlowType", e);
				}
				// +1 to get SSA val
				return pa.getHeapModel().getPointerKeyForLocal(node,
						flow.getArgNum() + 1);
			}

			@Override
			public PointerKey visitReturnFlow(
					ReturnFlow<IExplodedBasicBlock> flow) {
				SSAInstruction inst = flow.getBlock().getLastInstruction();
				if (inst == null) {
					Iterator<BasicBlockInContext<IExplodedBasicBlock>> it = graph
							.getPredNodes(flow.getBlock());
					if (it.hasNext()) {
						BasicBlockInContext<IExplodedBasicBlock> realBlock = it
								.next();
						inst = realBlock.getLastInstruction();
					} else {
						logger.error("synthetic return flow with no predecessor: probably shouldn't happen");
						throw new IllegalArgumentException();
					}
				}
				int val;
				// now we have to handle the two variants of this flow.
				if (flow.isSource()) {
					// If it's a source, then this represents the return value of an
					// invoked method, so we use the getDef value.
					val = ((SSAInvokeInstruction) inst).getReturnValue(0);
				} else {
					// If it's a sink, then we use the getUse value.
					val = ((SSAReturnInstruction) inst).getResult();
					assert val != -1;
				}				
				return pa.getHeapModel().getPointerKeyForLocal(node, val);
			}
		});
	}

	private CGNode nodeForMethod(IMethod method) {
		return cg.getNode(method, Everywhere.EVERYWHERE);
	}

}
