package org.scandroid;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.BasicConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spec.ISpecs;
import synthMethod.MethodAnalysis;
import synthMethod.XMLSummaryWriter;
import util.AndroidAppLoader;
import util.ThrowingSSAInstructionVisitor;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.dataflow.IFDS.ICFGSupergraph;
import com.ibm.wala.dataflow.IFDS.ISupergraph;
import com.ibm.wala.dataflow.IFDS.PathEdge;
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
import com.ibm.wala.ipa.callgraph.propagation.InstanceFieldKey;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.LocalPointerKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ipa.callgraph.propagation.StaticFieldKey;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ipa.summaries.MethodSummary;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.collections.Filter;
import com.ibm.wala.util.graph.traverse.DFSPathFinder;
import com.ibm.wala.util.intset.OrdinalSet;
import com.ibm.wala.util.strings.StringStuff;

import domain.CodeElement;
import domain.DomainElement;
import domain.FieldElement;
import domain.IFDSTaintDomain;
import domain.LocalElement;
import flow.FlowAnalysis;
import flow.InflowAnalysis;
import flow.OutflowAnalysis;
import flow.types.FieldFlow;
import flow.types.FlowType;
import flow.types.IKFlow;
import flow.types.ParameterFlow;
import flow.types.ReturnFlow;

public class Summarizer<E extends ISSABasicBlock> {
	public static final Logger logger = LoggerFactory.getLogger(Summarizer.class);
    public static final String WALA_NATIVES_XML = 
            "wala/wala-src/com.ibm.wala.core/dat/natives.xml";

	/**
	 * @param args
	 * @throws IOException
	 * @throws CallGraphBuilderCancelException
	 * @throws ClassHierarchyException
	 */
	public static void main(String[] args) throws ClassHierarchyException,
			CallGraphBuilderCancelException, IOException {
		BasicConfigurator.configure();
		if (args.length < 2) {
			logger.error("Usage: Summarizer <jarfile> <methoddescriptor>");
			System.exit(1);
		}

		String appJar = args[0];
		String methoddescriptor = args[1];

		Summarizer<IExplodedBasicBlock> s = new Summarizer<IExplodedBasicBlock>(
				appJar, methoddescriptor);

		System.out.println(s.summarize());
	}

	private final String appJar;
	private final String methodDescriptor;
	private ISupergraph<BasicBlockInContext<E>, CGNode> graph;
	private CallGraph cg;
	private PointerAnalysis pa;
	private MethodReference methodRef;
	private AnalysisScope scope;
	private ClassHierarchy cha;

	public Summarizer(String appJar, String methoddescriptor) throws IOException, ClassHierarchyException {
		this.appJar = appJar;
		this.methodDescriptor = methoddescriptor;
		this.methodRef = StringStuff.makeMethodReference(methodDescriptor);
        this.scope = DexAnalysisScopeReader.makeAndroidBinaryAnalysisScope(appJar, 
		   new File("conf/Java60RegressionExclusions.txt"));
        this.cha = ClassHierarchy.make(scope);
	}

	private String summarize() throws ClassHierarchyException,
			CallGraphBuilderCancelException, IOException {

		// Map<FlowType<IExplodedBasicBlock>,
		// Set<FlowType<IExplodedBasicBlock>>> summaryMap =
		// runDFAnalysis(appJar);
		Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>> dfAnalysis = runDFAnalysis();
		logger.debug(dfAnalysis.toString());
		
		MethodSummary summary = new MethodSummary(methodRef);
		
		
		Collection<IMethod> entryMethods = cha.getPossibleTargets(methodRef);
		if (entryMethods.size() != 1) {
			logger.error("More than one imethod found for: "+methodRef);
		}
		
		IMethod imethod = entryMethods.iterator().next();
		
		List<SSAInstruction> instructions = compileFlowType(imethod, dfAnalysis);
		
		for (SSAInstruction inst : instructions) {
			summary.addStatement(inst);
		}
		
		XMLSummaryWriter writer = new XMLSummaryWriter();
		writer.add(summary);
		
		return writer.serialize();
	}
	
	private
    Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>> 
    runDFAnalysis() 
        throws IOException, ClassHierarchyException, 
               CallGraphBuilderCancelException {        
		Iterable<Entrypoint> entrypoints = ImmutableList
				.<Entrypoint> of(new DefaultEntrypoint(methodRef, cha));
		
		ISpecs specs = new MethodSummarySpecs(methodRef);
                
        AnalysisOptions options = new AnalysisOptions(scope, entrypoints);
        CallGraphBuilder builder = 
        		makeCallgraph(scope, cha, options, WALA_NATIVES_XML);

        
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
          flowResult = FlowAnalysis.analyze(sg, cg, pa, initialTaints, domain, null);
        
        Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>>
          permissionOutflow = OutflowAnalysis.analyze(cg, cha, sg, pa, flowResult, domain, specs);
        
        return permissionOutflow;
    }

//	private void runDFAnalysis(String appJar) throws IOException,
//			ClassHierarchyException, CallGraphBuilderCancelException {
//
//		AnalysisScope scope = DexAnalysisScopeReader
//				.makeAndroidBinaryAnalysisScope(appJar, new File(
//						"conf/Java60RegressionExclusions.txt"));
//		ClassHierarchy cha = ClassHierarchy.make(scope);
//
//		MethodReference methodRef = StringStuff
//				.makeMethodReference(methodDescriptor);
//		Iterable<Entrypoint> entrypoints = ImmutableList
//				.<Entrypoint> of(new DefaultEntrypoint(methodRef, cha));
//
//		AnalysisOptions options = new AnalysisOptions(scope, entrypoints);
//		CallGraphBuilder builder = makeCallgraph(scope, cha, options, null);
//
//		CallGraph cg = builder.makeCallGraph(options, null);
//
//		graph = null; // ICFGSupergraph.make(cg, builder.getAnalysisCache());
//		PointerAnalysis pa = builder.getPointerAnalysis();
//
//		
//		ISpecs methodSummarySpecs = new MethodSummarySpecs(methodRef);
//				
//		// Map<BasicBlockInContext<IExplodedBasicBlock>,
//		// Map<FlowType<IExplodedBasicBlock>, Set<CodeElement>>> initialTaints =
//		// InflowAnalysis.analyze(cg, cha, sg, pa, new HashMap<InstanceKey,
//		// String>(), specs);
//		IFDSTaintDomain<E> domain = new IFDSTaintDomain<E>();
//
//		// Collection<IMethod> entryMethods = cha.getPossibleTargets(methodRef);
//		// final IMethod entryMethod;
//		// if (1 != entryMethods.size()) {
//		// System.err.println("Too many IMethods for method reference "
//		// + "(or none at all).  found: " + entryMethods.size());
//		// throw new IllegalArgumentException();
//		// } else {
//		// entryMethod = entryMethods.iterator().next();
//		// }
//		//
//		// MethodAnalysis<E> methodAnalysis = new MethodAnalysis<E>(
//		// new Predicate<IMethod>() {
//		// @Override
//		// public boolean test(IMethod im) {
//		// return im.equals(entryMethod);
//		// }
//		// });
//		// methodAnalysis.analyze(graph, pa, null,
//		// graph.getEntriesForProcedure(cg
//		// .getNode(entryMethod, Everywhere.EVERYWHERE))[0]);
//		//
//		// System.out.println(methodAnalysis.newSummaries);
//
//		//
//		// Map<BasicBlockInContext<IExplodedBasicBlock>,
//		// Map<FlowType<IExplodedBasicBlock>, Set<CodeElement>>>
//		// initialTaints = setUpTaints(sg, cg, pa, domain, entryMethod);
//		//
//		// TabulationResult<BasicBlockInContext<IExplodedBasicBlock>, CGNode,
//		// DomainElement>
//		// flowResult = FlowAnalysis.analyze(sg, cg, pa, initialTaints, domain,
//		// null);
//		//
//		// // Map<FlowType<IExplodedBasicBlock>,
//		// // Set<FlowType<IExplodedBasicBlock>>>
//		// // permissionOutflow = OutflowAnalysis.analyze(cg, cha, sg, pa,
//		// // flowResult, domain, specs);
//		// System.out.println(flowResult);
//		//
//		// return makeSummary(flowResult);
//	}

	private CallGraphBuilder makeCallgraph(AnalysisScope scope,
			ClassHierarchy cha, AnalysisOptions options,
			String methodSummariesFile) throws FileNotFoundException {

		CallGraphBuilder builder = AndroidAppLoader.makeZeroCFABuilder(options,
				new AnalysisCache(), cha, scope, null, null, null, null);

		return builder;
	}

	private Map<BasicBlockInContext<E>, Map<FlowType<E>, Set<CodeElement>>> setUpTaints(
			final ISupergraph<BasicBlockInContext<E>, CGNode> graph,
			CallGraph cg, PointerAnalysis pa, final IFDSTaintDomain<E> domain,
			IMethod entryMethod) {
		Map<BasicBlockInContext<E>, Map<FlowType<E>, Set<CodeElement>>> taintMap = Maps
				.newHashMap();

		CGNode node = cg.getNode(entryMethod, Everywhere.EVERYWHERE);

		for (BasicBlockInContext<E> block : graph.getEntriesForProcedure(node)) {
			taintMap.put(block, buildTaintMap(domain, entryMethod, block));
		}
		return taintMap;
	}

	private Map<FlowType<E>, Set<CodeElement>> buildTaintMap(
			final IFDSTaintDomain<E> domain, IMethod entryMethod,
			BasicBlockInContext<E> methEntryBlock) {

		final List<PathEdge<BasicBlockInContext<E>>> initialEdges = Lists
				.newArrayList();
		Map<FlowType<E>, Set<CodeElement>> taintMap = Maps.newHashMap();

		Set<DomainElement> initialTaints = Sets.newHashSet();

		// Add PathEdges to the initial taints.
		// In this case, taint all parameters into the method call
		for (int i = 0; i < entryMethod.getNumberOfParameters(); i++) {
			DomainElement de = new DomainElement(new LocalElement(i + 1),
					new ParameterFlow<E>(methEntryBlock, i, true));
			initialTaints.add(de);

			// taint the parameter:
			int taint = domain.getMappedIndex(de);
			initialEdges.add(PathEdge.createPathEdge(methEntryBlock, 0,
					methEntryBlock, taint));

			// taint the fields on the parameter:
			TypeReference paramTR = entryMethod.getParameterType(i);
			IClass paramClass = pa.getClassHierarchy().lookupClass(paramTR);

			if (paramTR.isPrimitiveType() || paramTR.isArrayType()
					|| paramClass == null) {
				continue;
			}

			Collection<IField> fields = paramClass.getAllFields();

			LocalPointerKey pointerKey = new LocalPointerKey(
					methEntryBlock.getNode(), methEntryBlock.getNode().getIR()
							.getParameter(i));

			OrdinalSet<InstanceKey> pointsToSet = pa.getPointsToSet(pointerKey);

			for (IField iField : fields) {
				taintField(iField, pointsToSet, methEntryBlock, domain,
						initialTaints, initialEdges, Sets.newHashSet(paramTR));
			}
		}

		if (entryMethod.isStatic()) {
			// we need to taint the static fields of the enclosing class.
			// if the method is *not* static, then these will have been tainted
			// during the tainting of parameters above.
			for (IField field : entryMethod.getDeclaringClass()
					.getAllStaticFields()) {
				Iterable<InstanceKey> staticInstances = pa
						.getPointsToSet(new StaticFieldKey(field));

				taintField(field, staticInstances, methEntryBlock, domain,
						initialTaints, initialEdges,
						Sets.<TypeReference> newHashSet());
			}
		}

		// TODO we don't currently taint static globals outside of the enclosing
		// class / method params.
		return taintMap;
	}

	private void taintField(IField myField,
			Iterable<InstanceKey> parentPointsToSet,
			BasicBlockInContext<E> methEntryBlock, IFDSTaintDomain<E> domain,
			Set<DomainElement> initialTaints,
			List<PathEdge<BasicBlockInContext<E>>> initialEdges,
			Set<TypeReference> taintedTypes) {

		TypeReference tr = myField.getFieldTypeReference();

		if (taintedTypes.contains(tr)) {
			// MyLogger.log(LogLevel.DEBUG,
			// "*not* re-tainting tainted type: "+myField);
			return;
		}

		// MyLogger.log(LogLevel.DEBUG, "tainting field: "+myField);
		taintedTypes.add(tr);

		Collection<PointerKey> pointerKeys = Lists.newArrayList();
		if (myField.isStatic()) {
			pointerKeys.add(new StaticFieldKey(myField));
		} else {
			for (InstanceKey ik : parentPointsToSet) {
				pointerKeys.add(new InstanceFieldKey(ik, myField));
			}
		}

		for (InstanceKey ik : parentPointsToSet) {
			DomainElement de = new DomainElement(new FieldElement(ik,
					myField.getReference(), myField.isStatic()),
					new FieldFlow<E>(methEntryBlock, myField, true));
			initialTaints.add(de);
			initialEdges.add(PathEdge.createPathEdge(methEntryBlock, 0,
					methEntryBlock, domain.getMappedIndex(de)));
		}

		// We need the all the instance keys for the field we're currently
		// tainting for the recursive case
		List<InstanceKey> iks = Lists.newArrayList();
		for (PointerKey pk : pointerKeys) {
			for (InstanceKey ik : pa.getPointsToSet(pk)) {
				iks.add(ik);
			}
		}

		IClassHierarchy cha = myField.getClassHierarchy();
		IClass fieldClass = cha.lookupClass(tr);

		// recurse on the fields of the myField:

		// Terminate recursion if myField is a primitive or an array
		// because they don't have fields.
		// Also, if the type is in the exclusions file (or doesn't exist for
		// some other reason...) then the class reference will be null.
		if (tr.isPrimitiveType() || tr.isArrayType() || fieldClass == null) {
			return;
		}

		Collection<IField> fields = fieldClass.getAllFields();

		for (IField field : fields) {
			taintField(field, iks, methEntryBlock, domain, initialTaints,
					initialEdges, taintedTypes);
		}
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
			final InstanceKey ik) {
		List<PointerKey> path = Lists.newArrayList();

		DFSPathFinder<Object> finder = new DFSPathFinder<Object>(
				pa.getHeapGraph(), pkSet.iterator(), new Filter<Object>() {
					public boolean accepts(Object o) {
						return (ik.equals(o));
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

	private List<SSAInstruction> compileFlowType(final IMethod method,
			final FlowType<E> ft) {
		// what's the largest SSA value that refers to a parameter?
		final int maxParam = method.getNumberOfParameters();
		// keep track of which SSA values have already been added to the result
		// list, and so can be referenced by subsequent instructions
		final BitSet refInScope = new BitSet();
		// set the implicit values for parameters
		refInScope.set(1, maxParam + 1);

		final CGNode node = cg.getNode(method, Everywhere.EVERYWHERE);
		final DefUse du = node.getDU();

		final List<SSAInstruction> insts = Lists.newArrayList();
		// in case order matters, add any return statements to this list, to be
		// combined at the end
		final List<SSAInstruction> returns = Lists.newArrayList();
		ft.visit(new FlowType.FlowTypeVisitor<E>() {

			final class PathWalker extends ThrowingSSAInstructionVisitor {

				public PathWalker() {
					super(new IllegalArgumentException("unhandled SSAInstruction"));
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
							paramInst.visit(new PathWalker());
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
				public void visitReturn(SSAReturnInstruction instruction) {
					// returns only have a single use (-1 if void return), so
					// walk that val if present and then add this instruction to
					// the return list

					int use = instruction.getUse(0);
					if (use != -1 && !refInScope.get(use)) {
						// use is not in scope yet
						SSAInstruction useInst = du.getDef(use);
						useInst.visit(new PathWalker());
					}
					// postcondition: use is now in scope, if present
					assert (use == -1 || refInScope.get(use));
					returns.add(instruction);
				}

			}

			@Override
			public void visitFieldFlow(FieldFlow<E> flow) {
				if (flow.getBlock().getLastInstructionIndex() != 0) {
					logger.warn("basic block with length other than 1: "
							+ flow.getBlock());
				}
				flow.getBlock().getLastInstruction().visit(new PathWalker());
			}

			@Override
			public void visitIKFlow(IKFlow<E> flow) {
				IllegalArgumentException e = new IllegalArgumentException(
						"shouldn't find any IKFlows");
				logger.error("exception compiling FlowType", e);
				throw e;
			}

			@Override
			public void visitParameterFlow(ParameterFlow<E> flow) {
				// ParameterFlow can be used in two ways. Here we handle the way
				// that references a parameter of the current method, and do
				// nothing. The other way involves arguments to method
				// invocations, and AT says we shouldn't see any of those
				// currently.

				// This loop detects the first case, where the block associated
				// with the flow is equal to the entry block of the method
				boolean equal = false;
				for (BasicBlockInContext<E> entryBlock : graph
						.getEntriesForProcedure(node)) {
					equal = equal || flow.getBlock().equals(entryBlock);
				}
				if (equal) {
					return;
				} else {
					IllegalArgumentException e = new IllegalArgumentException(
							"shouldn't have any ParameterFlows for invoked arguments");
					logger.error("exception compiling FlowType", e);
				}
			}

			@Override
			public void visitReturnFlow(ReturnFlow<E> flow) {
				if (flow.getBlock().getLastInstructionIndex() != 0) {
					logger.warn("basic block with length other than 1: "
							+ flow.getBlock());
				}
				SSAInstruction inst = flow.getBlock().getLastInstruction();
				// Two cases here:
				// 1. source == true: block should be an invoke instruction
				// 2. source == false: block should be a return instruction
				// handle both by invoking the PathWalker to ensure all relevant
				// refs are in scope
				inst.visit(new PathWalker());
			}
		});
		insts.addAll(returns);
		return insts;
	}

	private Object getKeyFromFlowType(FlowType ft) {
		if (ft instanceof FieldFlow) {
			FieldFlow ff = (FieldFlow) ft;
			int val = ff.getBlock().getLastInstruction().getUse(0);
			return (Object) new LocalPointerKey(graph.getProcOf(ff.getBlock()),
					val);
		} else if (ft instanceof IKFlow) {
			return (Object) ((IKFlow) ft).getIK();
		} else if (ft instanceof ParameterFlow) {
			// TODO: is this right? The whole point of this method is to lookup
			// symbols in the environment that we introduce. In the case of
			// parameters, we don't introduce them, they're arg0, arg1, etc.
			return null;
		} else if (ft instanceof ReturnFlow) {
			ReturnFlow rf = (ReturnFlow) ft;
			// TODO: figure this out on Monday
		}
		return null;
	}

	private CGNode nodeForMethod(IMethod method) {
		return cg.getNode(method, Everywhere.EVERYWHERE);
	}

}
