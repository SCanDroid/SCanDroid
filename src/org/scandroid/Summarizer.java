package org.scandroid;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import synthMethod.MethodAnalysis;
import util.AndroidAppLoader;

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
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.Predicate;
import com.ibm.wala.util.intset.OrdinalSet;
import com.ibm.wala.util.strings.StringStuff;

import domain.CodeElement;
import domain.DomainElement;
import domain.FieldElement;
import domain.IFDSTaintDomain;
import domain.LocalElement;
import flow.types.FieldFlow;
import flow.types.FlowType;
import flow.types.ParameterFlow;

public class Summarizer<E extends ISSABasicBlock> {

	/**
	 * @param args
	 * @throws IOException
	 * @throws CallGraphBuilderCancelException
	 * @throws ClassHierarchyException
	 */
	public static void main(String[] args) throws ClassHierarchyException,
			CallGraphBuilderCancelException, IOException {
		if (args.length < 2) {
			System.err
					.println("Usage: Summarizer <jarfile> <methoddescriptor>");
			System.exit(1);
		}

		String appJar = args[0];
		String methoddescriptor = args[1];

		Summarizer<IExplodedBasicBlock> s = new Summarizer<IExplodedBasicBlock>(appJar, methoddescriptor);

		System.out.println(s.summarize());
	}

	private final String appJar;
	private final String methodDescriptor;
	private ISupergraph<BasicBlockInContext<E>, CGNode> graph;

	public Summarizer(String appJar, String methoddescriptor) {
		this.appJar = appJar;
		this.methodDescriptor = methoddescriptor;
	}

	private String summarize() throws ClassHierarchyException,
			CallGraphBuilderCancelException, IOException {

		// Map<FlowType<IExplodedBasicBlock>,
		// Set<FlowType<IExplodedBasicBlock>>> summaryMap =
		// runDFAnalysis(appJar);
		runDFAnalysis(appJar);
		return "not yet summarized";
	}

	private void runDFAnalysis(String appJar) throws IOException,
			ClassHierarchyException, CallGraphBuilderCancelException {

		AnalysisScope scope = DexAnalysisScopeReader
				.makeAndroidBinaryAnalysisScope(appJar, new File(
						"conf/Java60RegressionExclusions.txt"));
		ClassHierarchy cha = ClassHierarchy.make(scope);

		MethodReference methodRef = StringStuff
				.makeMethodReference(methodDescriptor);
		Iterable<Entrypoint> entrypoints = ImmutableList
				.<Entrypoint> of(new DefaultEntrypoint(methodRef, cha));

		AnalysisOptions options = new AnalysisOptions(scope, entrypoints);
		CallGraphBuilder builder = makeCallgraph(scope, cha, options, null);

		CallGraph cg = builder.makeCallGraph(options, null);

		graph = ICFGSupergraph.make(cg, builder.getAnalysisCache());
		PointerAnalysis pa = builder.getPointerAnalysis();

		// Map<BasicBlockInContext<IExplodedBasicBlock>,
		// Map<FlowType<IExplodedBasicBlock>, Set<CodeElement>>> initialTaints =
		// InflowAnalysis.analyze(cg, cha, sg, pa, new HashMap<InstanceKey,
		// String>(), specs);
		IFDSTaintDomain<E> domain = new IFDSTaintDomain<E>();

		Collection<IMethod> entryMethods = cha.getPossibleTargets(methodRef);

		final IMethod entryMethod;
		if (1 != entryMethods.size()) {
			System.err.println("Too many IMethods for method reference "
					+ "(or none at all).  found: " + entryMethods.size());
			throw new IllegalArgumentException();
		} else {
			entryMethod = entryMethods.iterator().next();
		}

		MethodAnalysis<E> methodAnalysis = new MethodAnalysis<E>(
				new Predicate<IMethod>() {
					@Override
					public boolean test(IMethod im) {
						return im.equals(entryMethod);
					}
				});
		methodAnalysis.analyze(graph, pa, null, graph.getEntriesForProcedure(cg
				.getNode(entryMethod, Everywhere.EVERYWHERE))[0]);

		System.out.println(methodAnalysis.newSummaries);

		//
		// Map<BasicBlockInContext<IExplodedBasicBlock>,
		// Map<FlowType<IExplodedBasicBlock>, Set<CodeElement>>>
		// initialTaints = setUpTaints(sg, cg, pa, domain, entryMethod);
		//
		// TabulationResult<BasicBlockInContext<IExplodedBasicBlock>, CGNode,
		// DomainElement>
		// flowResult = FlowAnalysis.analyze(sg, cg, pa, initialTaints, domain,
		// null);
		//
		// // Map<FlowType<IExplodedBasicBlock>,
		// // Set<FlowType<IExplodedBasicBlock>>>
		// // permissionOutflow = OutflowAnalysis.analyze(cg, cha, sg, pa,
		// // flowResult, domain, specs);
		// System.out.println(flowResult);
		//
		// return makeSummary(flowResult);
	}

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
			taintMap.put(block,
					buildTaintMap(graph, cg, pa, domain, entryMethod, block));
		}
		return taintMap;
	}

	private Map<FlowType<E>, Set<CodeElement>> buildTaintMap(
			final ISupergraph<BasicBlockInContext<E>, CGNode> graph,
			CallGraph cg, PointerAnalysis pa, final IFDSTaintDomain<E> domain,
			IMethod entryMethod, BasicBlockInContext<E> methEntryBlock) {

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
				taintField(pa, iField, pointsToSet, methEntryBlock, domain,
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

				taintField(pa, field, staticInstances, methEntryBlock, domain,
						initialTaints, initialEdges,
						Sets.<TypeReference> newHashSet());
			}
		}

		// TODO we don't currently taint static globals outside of the enclosing
		// class / method params.
		return taintMap;
	}

	private void taintField(PointerAnalysis pa,
			IField myField, Iterable<InstanceKey> parentPointsToSet,
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
			taintField(pa, field, iks, methEntryBlock, domain, initialTaints,
					initialEdges, taintedTypes);
		}
	}

	public Set<PointerKey> getInputPointerKeys(IMethod method) {

		return null;
	}

}
