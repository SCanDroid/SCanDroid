package org.scandroid;

import java.io.File;
import java.io.FileInputStream;
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
import util.AndroidAnalysisContext;
import util.ThrowingSSAInstructionVisitor;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.JavaLanguage;
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
import com.ibm.wala.ipa.callgraph.propagation.ClassBasedInstanceKeys;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKeyFactory;
import com.ibm.wala.ipa.callgraph.propagation.LocalPointerKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.summaries.MethodSummary;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAArrayLoadInstruction;
import com.ibm.wala.ssa.SSACheckCastInstruction;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInstructionFactory;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.MonitorUtil.IProgressMonitor;
import com.ibm.wala.util.collections.Filter;
import com.ibm.wala.util.collections.Pair;
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
	private static final Logger logger = LoggerFactory
			.getLogger(Summarizer.class);

	private static final long TIME_LIMIT = 60 * 60;
	public static final String WALA_NATIVES_XML = "../WALA/com.ibm.wala.core/dat/natives.xml";

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
			logger.error("Usage: Summarizer <jarfile> <methoddescriptor> [static|notstatic]");
			logger.error("   methoddescriptor -- a specification of a java method, formatted as:");
			logger.error("                       some.package.Clasas(Ljava/lang/String;I)Ljava/lang/String;");
			System.exit(1);
		}

		String appJar = args[0];
		String methoddescriptor = args[1];
		// boolean isStatic = false;
		// if (args[2].equals("static")) {
		// isStatic = true;
		// }

		Summarizer<IExplodedBasicBlock> s = new Summarizer<IExplodedBasicBlock>(
				appJar);
		s.summarize(methoddescriptor, null);

		System.out.println(s.serialize());
	}

	private final AnalysisScope scope;
	private final ClassHierarchy cha;
	private CallGraph cg;
	private PointerAnalysis pa;
	private ISupergraph<BasicBlockInContext<IExplodedBasicBlock>, CGNode> graph;
	private XMLSummaryWriter writer;

	private AnalysisOptions options;

	public Summarizer(String appJar) throws IOException,
			ClassHierarchyException, IllegalArgumentException,
			CallGraphBuilderCancelException, ParserConfigurationException {
		this.scope = DexAnalysisScopeReader.makeAndroidBinaryAnalysisScope(
				appJar, new File("conf/Java60RegressionExclusions.txt"));
		this.cha = ClassHierarchy.make(scope);
		writer = new XMLSummaryWriter();
	}

	public void summarize(String methodDescriptor)
			throws ClassHierarchyException, CallGraphBuilderCancelException,
			IOException, ParserConfigurationException {
		summarize(methodDescriptor, new TimedMonitor(TIME_LIMIT));
	}

	public void summarize(String methodDescriptor, IProgressMonitor monitor)
			throws ClassHierarchyException, CallGraphBuilderCancelException,
			IOException, ParserConfigurationException {

		MethodReference methodRef = StringStuff
				.makeMethodReference(methodDescriptor);

		Collection<IMethod> entryMethods = cha.getPossibleTargets(methodRef);
		if (entryMethods.size() != 1) {
			logger.error("More than one imethod found for: " + methodRef);
		}
		IMethod imethod = entryMethods.iterator().next();

		MethodSummary summary = new MethodSummary(methodRef);
		summary.setStatic(imethod.isStatic());

		Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>> dfAnalysis = runDFAnalysis(
				summary, WALA_NATIVES_XML, monitor);
		logger.debug(dfAnalysis.toString());

		List<SSAInstruction> instructions = compileFlowMap(imethod, dfAnalysis);

		if (0 == instructions.size()) {
			logger.warn("No instructions in summary for " + methodDescriptor);
			return;
		}

		for (SSAInstruction inst : instructions) {
			summary.addStatement(inst);
		}

		writer.add(summary);
	}

	/**
	 * Generate XML for these summaries.
	 * 
	 * @return
	 */
	public String serialize() {
		return writer.serialize();
	}

	public Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>> runDFAnalysis(
			MethodSummary mSummary) throws ClassHierarchyException,
			CallGraphBuilderCancelException, IOException {
		return runDFAnalysis(mSummary, WALA_NATIVES_XML, new TimedMonitor(
				TIME_LIMIT));
	}

	public Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>> runDFAnalysis(
			MethodSummary mSummary, String methodSummariesFile)
			throws ClassHierarchyException, CallGraphBuilderCancelException,
			IOException {
		return runDFAnalysis(mSummary, methodSummariesFile, new TimedMonitor(
				TIME_LIMIT));
	}

	public Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>> runDFAnalysis(
			MethodSummary mSummary, String methodSummariesFile,
			IProgressMonitor monitor) throws IOException,
			ClassHierarchyException, CallGraphBuilderCancelException {

		MethodReference methodRef = (MethodReference) mSummary.getMethod();
		Iterable<Entrypoint> entrypoints = ImmutableList
				.<Entrypoint> of(new DefaultEntrypoint(methodRef, cha));
		options = new AnalysisOptions(scope, entrypoints);

		CallGraphBuilder builder = makeCallgraph(scope, cha, options,
				methodSummariesFile);
		cg = builder.makeCallGraph(options, null);
		pa = builder.getPointerAnalysis();
		graph = ICFGSupergraph.make(cg, builder.getAnalysisCache());

		ISpecs specs = new MethodSummarySpecs(mSummary);

		Map<BasicBlockInContext<IExplodedBasicBlock>, Map<FlowType<IExplodedBasicBlock>, Set<CodeElement>>> initialTaints = InflowAnalysis
				.analyze(cg, cha, graph, pa,
						new HashMap<InstanceKey, String>(), specs);

		System.out.println("  InitialTaints count: " + initialTaints.size());

		IFDSTaintDomain<IExplodedBasicBlock> domain = new IFDSTaintDomain<IExplodedBasicBlock>();
		TabulationResult<BasicBlockInContext<IExplodedBasicBlock>, CGNode, DomainElement> flowResult = FlowAnalysis
				.analyze(graph, cg, pa, initialTaints, domain, null, monitor);

		Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>> permissionOutflow = OutflowAnalysis
				.analyze(cg, cha, graph, pa, flowResult, domain, specs);

		return permissionOutflow;
	}

	private CallGraphBuilder makeCallgraph(AnalysisScope scope,
			ClassHierarchy cha, AnalysisOptions options,
			String methodSummariesFile) throws FileNotFoundException {

		CallGraphBuilder builder = AndroidAnalysisContext.makeZeroCFABuilder(options,
				new AnalysisCache(), cha, scope, null, null,
				new FileInputStream(methodSummariesFile), null);

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
		// TODO: Broken! Doesn't follow field accesses
		List<PointerKey> path = Lists.newArrayList();

		final Iterator<Object> iterator = (Iterator) pkSet.iterator();
		DFSPathFinder<Object> finder = DFSPathFinder.newDFSPathFinder(
				pa.getHeapGraph(), iterator, new Filter<Object>() {
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
			final Pair<List<SSAInstruction>, Integer> lhs = compileFlowType(
					method, entry.getKey(), refInScope);
			insts.addAll(lhs.fst);
			for (FlowType<IExplodedBasicBlock> flow : entry.getValue()) {
				insts.addAll(compileFlowType(method, flow, refInScope, lhs.snd).fst);
			}
		}
		logger.debug("compiled flowMap: " + insts.toString());
		return insts;

	}

	private Pair<List<SSAInstruction>, Integer> compileFlowType(
			final IMethod method, final FlowType<IExplodedBasicBlock> ft,
			final BitSet refInScope) {
		return compileFlowType(method, ft, refInScope, -1);
	}

	/**
	 * Compile a FlowType into a list of SSA instructions representing that
	 * flow. If walking backwards from the FlowType's enclosed SSA instruction
	 * does not reach the lhsVal normally, we emit extra instructions to
	 * complete the chain, even if they do not yield type-correct code. If
	 * called on the LHS of a mapping, the lhsVal with be -1.
	 * 
	 * We accomplish this by checking whether the chain is complete after each
	 * recursive call to the PathWalker. If it happens to not be complete, we
	 * ignore the SSA vals that are actually there, eg, the ref field of a field
	 * get, and use the val from the LHS instead.
	 * 
	 * @param method
	 * @param ft
	 * @param refInScope
	 * @param lhsVal
	 * @return
	 */
	private Pair<List<SSAInstruction>, Integer> compileFlowType(
			final IMethod method, final FlowType<IExplodedBasicBlock> ft,
			final BitSet refInScope, final int lhsVal) {
		// what's the largest SSA value that refers to a parameter?
		final int maxParam = method.getNumberOfParameters();
		// set the implicit values for parameters
		refInScope.set(1, maxParam + 1);

		final CGNode node = nodeForMethod(method);
		final DefUse du = node.getDU();
		final SSAInstructionFactory instFactory = new JavaLanguage()
				.instructionFactory();

		final List<SSAInstruction> insts = Lists.newArrayList();
		// in case order matters, add any return statements to this list, to be
		// combined at the end
		final List<SSAInstruction> returns = Lists.newArrayList();
		Integer val = ft
				.visit(new FlowType.FlowTypeVisitor<IExplodedBasicBlock, Integer>() {
					final class PathWalker extends
							ThrowingSSAInstructionVisitor {
						public boolean completedChain = false || lhsVal == -1;

						public PathWalker() {
							super(new IllegalArgumentException(
									"unhandled SSAInstruction"));
						}

						@Override
						public void visitArrayLoad(
								SSAArrayLoadInstruction instruction) {
//							final int ref = instruction.getArrayRef();
//							if (ref != -1 && !refInScope.get(ref)) {
//								// ref is not in scope yet, so find the SSA
//								// instruction that brings it into scope
//								SSAInstruction refInst = du.getDef(ref);
//								if (refInst != null) {
//									refInst.visit(this);
//									completedChain = completedChain
//											|| ref == lhsVal;
//									// postcondition: ref is now in scope
//									;
//									assert ref == -1 || refInScope.get(ref)
//											|| !completedChain;
//								}
//							}
//
//							final int def = instruction.getDef();
//							// since wala can't read arrayload tags in
//							// summaries, just turn this into a checked cast
//							// (sloppy...)
//							SSAInstruction newInstruction = instFactory
//									.CheckCastInstruction(def,
//											completedChain ? ref : lhsVal,
//											instruction.getElementType(), true);
//
//							// if this val is already in scope, don't emit more
//							// instructions
//							if (refInScope.get(def)) {
//								return;
//							} else {
//								insts.add(newInstruction);
//								refInScope.set(def);
//							}
						}

						@Override
						public void visitGet(SSAGetInstruction instruction) {
							final int ref = instruction.getRef();
							if (ref != -1 && !refInScope.get(ref)) {
								// ref is not in scope yet, so find the SSA
								// instruction that brings it into scope
								SSAInstruction refInst = du.getDef(ref);
								if (refInst != null) {
									refInst.visit(this);
									completedChain = completedChain
											|| ref == lhsVal;
									// postcondition: ref is now in scope
									;
									assert ref == -1 || refInScope.get(ref)
											|| !completedChain;
								}
							}

							final int def = instruction.getDef();
							if (!completedChain) {
								if (!refInScope.get(lhsVal)) {
									du.getDef(lhsVal).visit(this);
									if (!completedChain) {
										logger.error("can't bring LHS into scope!");
									}
								}
								// if we haven't completed the chain by now,
								// stuff the lhsVal into the field
								FieldReference field = instruction
										.getDeclaredField();
								if (instruction.isStatic()) {
									logger.error("impossible");
								} else {
									instruction = instFactory.GetInstruction(
											def, lhsVal, field);
								}
							}

							// if this val is already in scope, don't emit more
							// instructions
							if (refInScope.get(def)) {
								return;
							} else {
								insts.add(instruction);
								refInScope.set(def);
							}
						}

						@Override
						public void visitPut(SSAPutInstruction instruction) {
							int ref = instruction.getRef();
							// a ref does not make a completed chain, since we
							// aren't getting the value from it
							if (ref != -1 && !refInScope.get(ref)) {
								// ref is not in scope yet, so find the SSA
								// instruction that brings it into scope
								SSAInstruction refInst = du.getDef(ref);
								assert refInst != null;
								refInst.visit(this);
							}
							// postcondition: ref is now in scope
							assert ref == -1 || refInScope.get(ref);

							int val = instruction.getVal();
							if (!refInScope.get(val)) {
								// if the RHS of the assignment is not in scope,
								// recur
								SSAInstruction valInst = du.getDef(val);
								if (valInst != null) {
									valInst.visit(this);
									// LHS flows into target field
									completedChain = completedChain
											|| val == lhsVal;
									// postcondition: val is now in scope
									assert refInScope.get(val)
											|| !completedChain;
								}
							}

							if (!completedChain) {
								if (!refInScope.get(lhsVal)) {
									du.getDef(lhsVal).visit(this);
									if (!completedChain) {
										logger.error("can't bring LHS into scope!");
									}
								}
								// if we haven't completed the chain by now,
								// stuff the lhsVal into the field
								FieldReference field = instruction
										.getDeclaredField();
								if (instruction.isStatic()) {
									instruction = instFactory.PutInstruction(
											lhsVal, field);
								} else {
									instruction = instFactory.PutInstruction(
											ref, lhsVal, field);
								}
							}
							insts.add(instruction);
						}

						@Override
						public void visitInvoke(SSAInvokeInstruction instruction) {

							// // get all the param refvals
							// int params[] = new int[instruction
							// .getNumberOfParameters()];
							// for (int paramIndex = 0; paramIndex <
							// params.length; paramIndex++) {
							// params[paramIndex] = instruction
							// .getUse(paramIndex);
							// }
							//
							// // make sure all params are in scope
							// for (int param : params) {
							// // LHS is a param?
							// completedChain = completedChain
							// || param == lhsVal;
							// if (!refInScope.get(param)) {
							// // ref is not in scope yet, so find the SSA
							// // instruction that brings it into scope
							// SSAInstruction paramInst = du.getDef(param);
							// paramInst.visit(this);
							// }
							// // postcondition: param is now in scope
							// assert refInScope.get(param);
							// }
							// // postcondition: all params are now in scope
							//
							// // if the chain is not yet completed, punt for
							// now
							// // and let the next level in the stack handle it,
							// // since we don't know which param "ought" to
							// have
							// // the link
							//
							// insts.add(instruction);
							// // only set refInScope if non-void:
							// if (instruction.getNumberOfReturnValues() == 1) {
							// // if this val is already in scope, don't emit
							// // more
							// // instructions, but check for completed chain
							// final int def = instruction.getReturnValue(0);
							// completedChain = completedChain
							// || def == lhsVal;
							// if (refInScope.get(def)) {
							// return;
							// } else {
							// insts.add(instruction);
							// refInScope.set(def);
							// }
							// }
						}

						@Override
						public void visitNew(SSANewInstruction instruction) {
//							final int def = instruction.getDef();
//							// I doubt this could complete a chain, but just in
//							// case:
//							completedChain = completedChain || def == lhsVal;
//							// if already in scope, do nothing
//							if (refInScope.get(def))
//								return;
//
//							// otherwise, just add the new instruction. Remember
//							// that constructors are handled as separate <init>
//							// methods
//							insts.add(instruction);
//							refInScope.set(def);
						}

						@Override
						public void visitReturn(SSAReturnInstruction instruction) {
							// returns only have a single use (-1 if void
							// return), so walk that val if present and then add
							// this
							// instruction to the return list
							int use = instruction.getUse(0);
							if (use != -1 && !refInScope.get(use)) {
								// use is not in scope yet
								SSAInstruction useInst = du.getDef(use);
								if (useInst != null) {
									completedChain = completedChain
											|| use == lhsVal;
									useInst.visit(this);
									// postcondition: use is now in scope, if
									// present
									assert (use == -1 || refInScope.get(use) || !completedChain);
								}

							}

							if (!completedChain) {
								// shove into return value if chain not
								// finished
								if (!refInScope.get(lhsVal)) {
									du.getDef(lhsVal).visit(this);
									if (!completedChain) {
										logger.error("can't bring LHS into scope!");
									}
								}
								instruction = instFactory.ReturnInstruction(
										lhsVal,
										instruction.returnsPrimitiveType());
							}
							returns.add(instruction);
						}

						@Override
						public void visitCheckCast(
								SSACheckCastInstruction instruction) {
//							final int val = instruction.getVal();
//							if (val != -1 && !refInScope.get(val)) {
//								// val is not in scope yet, so find the SSA
//								// instruction that brings it into scope
//								SSAInstruction valInst = du.getDef(val);
//								if (valInst != null) {
//									valInst.visit(this);
//									completedChain = completedChain
//											|| val == lhsVal;
//									// postcondition: val is now in scope
//									assert val == -1 || refInScope.get(val)
//											|| !completedChain;
//								}
//							}
//
//							final int def = instruction.getDef();
//							if (!completedChain) {
//								if (!refInScope.get(lhsVal)) {
//									du.getDef(lhsVal).visit(this);
//									if (!completedChain) {
//										logger.error("can't bring LHS into scope!");
//									}
//								}
//								instruction = instFactory.CheckCastInstruction(
//										def, lhsVal,
//										instruction.getDeclaredResultTypes(),
//										instruction.isPEI());
//							}
//
//							// if this val is already in scope, don't emit more
//							// instructions
//							if (refInScope.get(def)) {
//								return;
//							} else {
//								returns.add(instruction);
//								refInScope.set(def);
//							}
						}

					}

					@Override
					public Integer visitFieldFlow(
							FieldFlow<IExplodedBasicBlock> flow) {
						if (flow.getBlock().getLastInstructionIndex() != 0) {
							logger.warn("basic block with length other than 1: "
									+ flow.getBlock());
						}
						final SSAInstruction inst = flow.getBlock()
								.getLastInstruction();
						inst.visit(new PathWalker());
						return inst.getDef();
					}

					@Override
					public Integer visitIKFlow(IKFlow<IExplodedBasicBlock> flow) {
						IllegalArgumentException e = new IllegalArgumentException(
								"shouldn't find any IKFlows");
						logger.error("exception compiling FlowType", e);
						throw e;
					}

					@Override
					public Integer visitParameterFlow(
							ParameterFlow<IExplodedBasicBlock> flow) {
						// ParameterFlow can be used in two ways. Here we handle
						// the way
						// that references a parameter of the current method,
						// and do
						// nothing. The other way involves arguments to method
						// invocations, and AT says we shouldn't see any of
						// those
						// currently.

						// This loop detects the first case, where the block
						// associated
						// with the flow is equal to the entry block of the
						// method
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
						return Integer.valueOf(flow.getArgNum() + 1);
					}

					@Override
					public Integer visitReturnFlow(
							ReturnFlow<IExplodedBasicBlock> flow) {
						if (flow.getBlock().getLastInstructionIndex() != 0) {
							logger.warn("basic block with length other than 1: "
									+ flow.getBlock());
						}
						SSAInstruction inst = flow.getBlock()
								.getLastInstruction();
						// TODO: SUPPOSEDLY Two cases here:
						// 1. source == true: block should be an invoke
						// instruction
						// 2. source == false: block should be a return
						// instruction
						// handle both by invoking the PathWalker to ensure all
						// relevant
						// refs are in scope
						if (inst == null) {
							Iterator<BasicBlockInContext<IExplodedBasicBlock>> it = graph
									.getPredNodes(flow.getBlock());
							while (it.hasNext() && inst == null) {
								BasicBlockInContext<IExplodedBasicBlock> realBlock = it
										.next();
								inst = realBlock.getLastInstruction();
							}
						}
						inst.visit(new PathWalker());

						// final PointerKey pkFromFlowType = getPKFromFlowType(
						// method, flow);
						// logger.debug("ReturnFlow PK: " + pkFromFlowType);
						// logger.debug("Path from params: "
						// + getAccessPath(getInputPointerKeys(method),
						// pkFromFlowType));
						return Integer.valueOf(inst.getDef());
					}
				});
		insts.addAll(returns);
		return Pair.make(insts, val);
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
					// If it's a source, then this represents the return value
					// of an
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
