/**
 *
 * Copyright (c) 2009-2012,
 *
 *  Galois, Inc. (Aaron Tomb <atomb@galois.com>, Rogan Creswick <creswick@galois.com>)
 *  Steve Suh    <suhsteve@gmail.com>
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. The names of the contributors may not be used to endorse or promote
 * products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 *
 */
package org.scandroid;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;

import org.scandroid.domain.CodeElement;
import org.scandroid.domain.DomainElement;
import org.scandroid.domain.IFDSTaintDomain;
import org.scandroid.flow.FlowAnalysis;
import org.scandroid.flow.InflowAnalysis;
import org.scandroid.flow.OutflowAnalysis;
import org.scandroid.flow.functions.TaintTransferFunctions;
import org.scandroid.flow.types.FieldFlow;
import org.scandroid.flow.types.FlowType;
import org.scandroid.flow.types.FlowType.FlowTypeVisitor;
import org.scandroid.flow.types.IKFlow;
import org.scandroid.flow.types.ParameterFlow;
import org.scandroid.flow.types.ReturnFlow;
import org.scandroid.flow.types.StaticFieldFlow;
import org.scandroid.spec.ISpecs;
import org.scandroid.spec.StaticSpecs;
import org.scandroid.synthmethod.DefaultSCanDroidOptions;
import org.scandroid.synthmethod.TestSpecs;
import org.scandroid.synthmethod.XMLSummaryWriter;
import org.scandroid.util.AndroidAnalysisContext;
import org.scandroid.util.CGAnalysisContext;
import org.scandroid.util.IEntryPointSpecifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.Language;
import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.dataflow.IFDS.TabulationResult;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.callgraph.impl.FakeRootMethod;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.summaries.MethodSummary;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInstructionFactory;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.MonitorUtil.IProgressMonitor;
import com.ibm.wala.util.debug.UnimplementedError;
import com.ibm.wala.util.strings.StringStuff;

/**
 * @author acfoltzer
 *
 * @param <E>
 */
/**
 * @author acfoltzer
 *
 * @param <E>
 */
/**
 * @author acfoltzer
 * 
 * @param <E>
 */
public class Summarizer<E extends ISSABasicBlock> {
	private static final Logger logger = LoggerFactory
			.getLogger(Summarizer.class);

	private static final long TIME_LIMIT = 60 * 60;
	public static final String WALA_NATIVES_XML = "data/MethodSummaries.xml";

	/**
	 * @param args
	 * @throws IOException
	 * @throws ClassHierarchyException
	 * @throws ParserConfigurationException
	 * @throws URISyntaxException
	 * @throws CancelException
	 * @throws IllegalArgumentException
	 */
	public static void main(String[] args) throws ClassHierarchyException,
			IOException, ParserConfigurationException,
			IllegalArgumentException, CancelException, URISyntaxException {

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

	private final AndroidAnalysisContext analysisContext;
	private XMLSummaryWriter writer;

	public Summarizer(final String appJar) throws IllegalArgumentException,
			ClassHierarchyException, IOException, CancelException,
			URISyntaxException, ParserConfigurationException {
		analysisContext = new AndroidAnalysisContext(
				new DefaultSCanDroidOptions() {
					@Override
					public URI getClasspath() {
						return new File(appJar).toURI();
					}

					@Override
					public boolean stdoutCG() {
						return false;
					}
				});
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
		summarize(methodDescriptor, monitor, ISpecs.EMPTY_SPECS);
	}

	/**
	 * Create summary with additional Specs to be combined with the standard
	 * method summary specs
	 * 
	 * @param signature
	 * @param timedMonitor
	 * @param sourceSinkSpecs
	 * @throws IOException
	 * @throws CallGraphBuilderCancelException
	 * @throws ClassHierarchyException
	 */
	public void summarize(String methodDescriptor, IProgressMonitor monitor,
			ISpecs additionalSpecs) throws IOException,
			ClassHierarchyException, CallGraphBuilderCancelException {

		MethodReference methodRef = StringStuff
				.makeMethodReference(methodDescriptor);

		Collection<IMethod> entryMethods = analysisContext.getClassHierarchy()
				.getPossibleTargets(methodRef);

		if (entryMethods.size() > 1) {
			logger.error("More than one imethod found for: " + methodRef);
		} else if (entryMethods.size() == 0) {
			logger.error("No method found for: " + methodRef);
		}

		final IMethod imethod = entryMethods.iterator().next();

		MethodSummary summary = new MethodSummary(methodRef);
		summary.setStatic(imethod.isStatic());

		CGAnalysisContext<IExplodedBasicBlock> cgContext = new CGAnalysisContext<IExplodedBasicBlock>(
				analysisContext, new IEntryPointSpecifier() {
					@Override
					public List<Entrypoint> specify(
							AndroidAnalysisContext analysisContext) {
						return Lists
								.newArrayList((Entrypoint) new DefaultEntrypoint(
										imethod, analysisContext
												.getClassHierarchy()));
					}
				});

		Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>> dfAnalysis = runDFAnalysis(
				cgContext, summary, monitor, additionalSpecs);
		logger.debug(dfAnalysis.toString());

		List<SSAInstruction> instructions = new MethodSummarizer(cgContext,
				imethod).summarizeFlows(dfAnalysis);

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
		logger.debug("Generated summary:\n{}", writer.serialize());
		return writer.serialize();
	}

	private Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>> runDFAnalysis(
			CGAnalysisContext<IExplodedBasicBlock> cgContext,
			MethodSummary mSummary) throws ClassHierarchyException,
			CallGraphBuilderCancelException, IOException {
		return runDFAnalysis(cgContext, mSummary, new TimedMonitor(TIME_LIMIT));
	}

	private Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>> runDFAnalysis(
			CGAnalysisContext<IExplodedBasicBlock> cgContext,
			MethodSummary mSummary, IProgressMonitor monitor)
			throws IOException, ClassHierarchyException,
			CallGraphBuilderCancelException {
		return runDFAnalysis(cgContext, mSummary, monitor, ISpecs.EMPTY_SPECS);
	}

	private Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>> runDFAnalysis(
			CGAnalysisContext<IExplodedBasicBlock> cgContext,
			MethodSummary mSummary, IProgressMonitor monitor,
			ISpecs additionalSpecs) {

		ISpecs specs = TestSpecs.combine(additionalSpecs, TestSpecs.combine(
				new MethodSummarySpecs(mSummary),
				new StaticSpecs(cgContext.getClassHierarchy(), mSummary
						.getMethod().getSignature())));

		Map<BasicBlockInContext<IExplodedBasicBlock>, Map<FlowType<IExplodedBasicBlock>, Set<CodeElement>>> initialTaints = InflowAnalysis
				.analyze(cgContext, new HashMap<InstanceKey, String>(), specs);

		System.out.println("  InitialTaints count: " + initialTaints.size());

		IFDSTaintDomain<IExplodedBasicBlock> domain = new IFDSTaintDomain<IExplodedBasicBlock>();
		TabulationResult<BasicBlockInContext<IExplodedBasicBlock>, CGNode, DomainElement> flowResult = FlowAnalysis
				.analyze(cgContext, initialTaints, domain, monitor,
						new TaintTransferFunctions<IExplodedBasicBlock>(domain,
								cgContext.graph, cgContext.pa, true));

		Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>> permissionOutflow = new OutflowAnalysis(
				cgContext, specs).analyze(flowResult, domain);

		return permissionOutflow;
	}

	/**
	 * A one-time-use context for a single method summarization. This manages
	 * various state built up by the summarization process such as the symbol
	 * table, maps from flows to SSA values, and the new (synthetic)
	 * instructions.
	 * 
	 * The approach here is meant to work _only_ for a 0CFA analysis. It relies
	 * on the fact that all objects of the same type are identified as one class
	 * in the pointer analysis. Specifically, whenever a summary needs a value
	 * of type T, it just emits a new instruction for type T and uses the
	 * resulting SSA value.
	 * 
	 * Here is a sketch of how this is meant to work:
	 * 
	 * 1. For each source in the flow map, make sure we have an SSA value in the
	 * synthetic method corresponding to that source.
	 * 
	 * 2. For each edge from source to one of its sinks, use the SSA value
	 * generated in step 1 to emit the SSA instructions necessary for the
	 * synthesized value to reach the same sink.
	 * 
	 * @author acfoltzer
	 * 
	 */
	private static class MethodSummarizer {
		private static final Logger logger = LoggerFactory
				.getLogger(Summarizer.MethodSummarizer.class);
		private static final SSAInstructionFactory instFactory = Language.JAVA
				.instructionFactory();

		private final List<SSAInstruction> insts = Lists.newArrayList();
		private final Map<SSAInvokeInstruction, SSAInvokeInstruction> invs = Maps
				.newHashMap();
		private final Map<TypeReference, Integer> objs = Maps.newHashMap();
		private final CGAnalysisContext<IExplodedBasicBlock> ctx;
		private final IMethod method;
		private final SymbolTable tbl;

		/**
		 * Populated by compileSources
		 */
		private final Map<FlowType<IExplodedBasicBlock>, Integer> sourceMap = Maps
				.newHashMap();

		public MethodSummarizer(CGAnalysisContext<IExplodedBasicBlock> ctx,
				IMethod method) {
			this.ctx = ctx;
			this.method = method;

			final int numParams = method.getNumberOfParameters();
			// symbol table initially has parameter values reserved
			this.tbl = new SymbolTable(numParams);

			// if any params are reference types, we can use them for later
			// value resolution
			for (int param = 0; param < numParams; param++) {
				final TypeReference typeRef = method.getParameterType(param);
				if (!typeRef.isPrimitiveType()) {
					objs.put(typeRef, Integer.valueOf(tbl.getParameter(param)));
				}
			}
		}

		public List<SSAInstruction> summarizeFlows(
				Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>> flowMap) {
			// step 1.
			for (FlowType<IExplodedBasicBlock> source : flowMap.keySet()) {
				compileSource(source);
			}
			// step 2.
			for (FlowType<IExplodedBasicBlock> source : sourceMap.keySet()) {
				for (FlowType<IExplodedBasicBlock> sink : flowMap.get(source)) {
					compileEdge(source, sink);
				}
			}
			logger.debug("summarized instructions: {}", insts);
			return insts;
		}

		/**
		 * Given a source (from the LHS of the flow map), create sufficient
		 * instructions to make an SSA value for the source. After running this
		 * method, the new instructions will be in insts, and sourceMap will
		 * contain the associated SSA value.
		 * 
		 * @param sources
		 */
		private void compileSource(FlowType<IExplodedBasicBlock> source) {
			source.visit(new FlowTypeVisitor<IExplodedBasicBlock, Void>() {

				@Override
				public Void visitFieldFlow(FieldFlow<IExplodedBasicBlock> flow) {
					// first create an object of the right type
					int ref = findOrCreateValue(flow.getField()
							.getDeclaringClass().getReference());
					// then deref the field
					int field = tbl.newSymbol();
					insts.add(instFactory.GetInstruction(field, ref, flow
							.getField().getReference()));
					// associate the dereffed value with this flow
					sourceMap.put(flow, Integer.valueOf(ref));
					return null;
				}

				@Override
				public Void visitIKFlow(IKFlow<IExplodedBasicBlock> flow) {
					// just create a new object of this type
					int ref = findOrCreateValue(flow.getIK().getConcreteType()
							.getReference());
					// associate the new object with this flow
					sourceMap.put(flow, Integer.valueOf(ref));
					return null;
				}

				@Override
				public Void visitParameterFlow(
						ParameterFlow<IExplodedBasicBlock> flow) {
					if (FakeRootMethod.isFromFakeRoot(flow.getBlock())) {
						// TODO: better understand this weird edge case
						return null;
					}

					// two cases: either this is a formal to the method
					// we're analyzing, or an actual to a method that writes
					// to some fields on its corresponding formal.

					if (flow.getBlock().isEntryBlock()) {
						// In the first case, we just associate the val
						// number with this flow
						sourceMap.put(flow, Integer.valueOf(tbl
								.getParameter(flow.getArgNum())));
						return null;
					} else {
						// In the second case, we have to synthesize a call
						// to the function
						SSAInvokeInstruction inv = (SSAInvokeInstruction) flow
								.getBlock().getDelegate().getInstruction();
						SSAInvokeInstruction synthInv = findOrCreateInvoke(inv);
						sourceMap.put(flow, Integer.valueOf(synthInv
								.getUse(flow.getArgNum())));
					}
					return null;
				}

				@Override
				public Void visitReturnFlow(ReturnFlow<IExplodedBasicBlock> flow) {
					// this will only be the case where we have a flow from
					// the result of an invoked method
					SSAInvokeInstruction inv = (SSAInvokeInstruction) flow
							.getBlock().getDelegate().getInstruction();
					SSAInvokeInstruction synthInv = findOrCreateInvoke(inv);
					sourceMap.put(flow, Integer.valueOf(synthInv.getDef()));
					return null;
				}

				@Override
				public Void visitStaticFieldFlow(
						StaticFieldFlow<IExplodedBasicBlock> flow) {
					// just create a static get instruction for this field
					int val = tbl.newSymbol();
					// TODO: this will create multiple get instructions if
					// more than one flow goes through a static field, but
					// the overhead isn't too bad
					insts.add(instFactory.GetInstruction(val, flow.getField()
							.getReference()));
					sourceMap.put(flow, Integer.valueOf(val));
					return null;
				}

			});
		}

		/**
		 * Given a source and a sink, create sufficient instructions to
		 * represent a flow from that source to that sink.
		 * 
		 * @param source
		 * @param sink
		 */
		private void compileEdge(FlowType<IExplodedBasicBlock> source,
				FlowType<IExplodedBasicBlock> sink) {
			final int sourceVal = sourceMap.get(source).intValue();
			sink.visit(new FlowTypeVisitor<IExplodedBasicBlock, Void>() {

				@Override
				public Void visitFieldFlow(FieldFlow<IExplodedBasicBlock> flow) {
					// first create an object to assign to
					int ref = findOrCreateValue(flow.getField()
							.getDeclaringClass().getReference());
					// then put sourceVal into the field
					insts.add(instFactory.PutInstruction(ref, sourceVal, flow
							.getField().getReference()));
					return null;
				}

				@Override
				public Void visitIKFlow(IKFlow<IExplodedBasicBlock> flow) {
					// TODO unused?
					throw new UnimplementedError("IKFlow as a sink?");
				}

				@Override
				public Void visitParameterFlow(
						ParameterFlow<IExplodedBasicBlock> flow) {
					// I don't think we need to do anything for the case where
					// this is a formal param of the summarized method
					if (flow.getBlock().isEntryBlock()) {
						return null;
					}

					// Otherwise we need to synthesize a call to the function
					// whose actual parameter is a sink.
					SSAInvokeInstruction inv = (SSAInvokeInstruction) flow
							.getBlock().getDelegate().getInstruction();
					findOrCreateInvoke(inv);
					return null;
				}

				@Override
				public Void visitReturnFlow(ReturnFlow<IExplodedBasicBlock> flow) {
					// recover return type
					TypeReference typeRef = method.getReturnType();
					// emit return instruction
					insts.add(instFactory.ReturnInstruction(sourceVal,
							typeRef.isPrimitiveType()));
					return null;
				}

				@Override
				public Void visitStaticFieldFlow(
						StaticFieldFlow<IExplodedBasicBlock> flow) {
					// emit field put
					insts.add(instFactory.PutInstruction(sourceVal, flow
							.getField().getReference()));
					return null;
				}
			});
		}

		/**
		 * Synthesize an invoke instruction corresponding to the given origina
		 * instruction. This makes some attempt to minimize the number of
		 * synthesized instructions by first checking if we've synthesized for
		 * this exact instruction before.
		 * 
		 * In addition to returning the synthesized instruction, this adds the
		 * instruction to insts.
		 * 
		 * @param inv
		 * @return
		 */
		private SSAInvokeInstruction findOrCreateInvoke(SSAInvokeInstruction inv) {
			SSAInvokeInstruction synthInv = invs.get(inv);
			if (synthInv != null) {
				// return existing instruction if we already have it
				return synthInv;
			}

			final MethodReference declaredTarget = inv.getDeclaredTarget();
			final int numParams = declaredTarget.getNumberOfParameters();
			int[] paramVals = new int[numParams];
			for (int i = 0; i < numParams; i++) {
				TypeReference paramType = null;

				// first try to find out the concrete type of the argument
				final CGNode node = ctx.cg.getNode(method,
						Everywhere.EVERYWHERE);
				final PointerKey pk = ctx.pa.getHeapModel()
						.getPointerKeyForLocal(node, inv.getUse(i));
				for (InstanceKey ik : ctx.pa.getPointsToSet(pk)) {
					paramType = ik.getConcreteType().getReference();
				}

				// if the pointer analysis doesn't know, we just use the
				// declared type. Note that this may be different than the
				// concrete type of the formal parameter
				if (paramType == null) {
					paramType = declaredTarget.getParameterType(i);
				}
				paramVals[i] = findOrCreateValue(paramType);
			}
			if (inv.hasDef()) {
				synthInv = instFactory.InvokeInstruction(inv.getDef(),
						paramVals, inv.getException(), inv.getCallSite());
			} else {
				synthInv = instFactory.InvokeInstruction(paramVals,
						inv.getException(), inv.getCallSite());
			}
			insts.add(synthInv);
			invs.put(inv, synthInv);
			return synthInv;
		}

		/**
		 * If a value of the correct type is already in scope, this returns the
		 * value number. Otherwise it creates it using the symbol table (for
		 * primitives), or by emitting a new instruction (for other types)
		 * 
		 * @param typeRef
		 * @return
		 */
		private int findOrCreateValue(TypeReference typeRef) {
			if (typeRef.isPrimitiveType()) {
				return findOrCreateConstant(typeRef);
			} else {
				return findOrCreateObject(typeRef);
			}
		}

		private int findOrCreateConstant(TypeReference paramType) {
			logger.debug("finding constant for {}", paramType);
			if (paramType.equals(TypeReference.Boolean)) {
				return tbl.getConstant(false);
			} else if (paramType.equals(TypeReference.Double)) {
				return tbl.getConstant(0d);
			} else if (paramType.equals(TypeReference.Float)) {
				return tbl.getConstant(0f);
			} else if (paramType.equals(TypeReference.Int)) {
				return tbl.getConstant(0);
			} else if (paramType.equals(TypeReference.Long)) {
				return tbl.getConstant(0l);
			} else if (paramType.equals(TypeReference.JavaLangString)) {
				return tbl.getConstant("");
			} else {
				logger.error("non-constant type reference {}", paramType);
				throw new RuntimeException();
			}
		}

		private int findOrCreateObject(TypeReference typeRef) {
			logger.debug("finding object {}", typeRef);
			Integer objVal = objs.get(typeRef);
			if (objVal != null) {
				// return existing value if we already have one
				return objVal.intValue();
			}
			int ref = tbl.newSymbol();
			insts.add(instFactory.NewInstruction(ref,
					NewSiteReference.make(0, typeRef)));
			objs.put(typeRef, Integer.valueOf(ref));
			return ref;
		}

	}
}
