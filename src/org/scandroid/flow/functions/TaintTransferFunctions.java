package org.scandroid.flow.functions;

import java.util.List;
import java.util.Set;

import org.scandroid.domain.CodeElement;
import org.scandroid.domain.FieldElement;
import org.scandroid.domain.IFDSTaintDomain;
import org.scandroid.domain.InstanceKeyElement;
import org.scandroid.domain.ReturnElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.dataflow.IFDS.IFlowFunction;
import com.ibm.wala.dataflow.IFDS.IFlowFunctionMap;
import com.ibm.wala.dataflow.IFDS.IReversibleFlowFunction;
import com.ibm.wala.dataflow.IFDS.ISupergraph;
import com.ibm.wala.dataflow.IFDS.IUnaryFlowFunction;
import com.ibm.wala.dataflow.IFDS.IdentityFlowFunction;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.SparseIntSet;

public class TaintTransferFunctions<E extends ISSABasicBlock> implements
		IFlowFunctionMap<BasicBlockInContext<E>> {
	private static final Logger logger = LoggerFactory
			.getLogger(TaintTransferFunctions.class);

	private final IFDSTaintDomain<E> domain;
	private final ISupergraph<BasicBlockInContext<E>, CGNode> graph;
	private final PointerAnalysis pa;

	public static final IntSet EMPTY_SET = new SparseIntSet();
	public static final IntSet ZERO_SET = SparseIntSet.singleton(0);

	private static final IReversibleFlowFunction IDENTITY_FN = new IdentityFlowFunction();

	public TaintTransferFunctions(IFDSTaintDomain<E> domain,
			ISupergraph<BasicBlockInContext<E>, CGNode> graph,
			PointerAnalysis pa) {
		this.domain = domain;
		this.graph = graph;
		this.pa = pa;
	}

	@Override
	public IUnaryFlowFunction getCallFlowFunction(BasicBlockInContext<E> src,
			BasicBlockInContext<E> dest, BasicBlockInContext<E> ret) {
		logger.debug("getCallFlowFunction");
		SSAInstruction srcInst = src.getLastInstruction();
		if (null == srcInst) {
			return IDENTITY_FN;
		}
		SSAInstruction destInst = dest.getLastInstruction();
		if (null == destInst) {
			return IDENTITY_FN;
		}

		CGNode node = src.getNode();
		// each use in an invoke instruction is a parameter to the invoked
		// method,
		// these are the uses:
		List<Set<CodeElement>> actualParams = getOrdInCodeElts(node, srcInst);
		List<Set<CodeElement>> formalParams = getOrdInCodeElts(node, destInst);

		return union(new GlobalIdentityFunction<E>(domain),
				new CallFlowFunction<E>(domain, actualParams, formalParams));
	}

	@Override
	public IUnaryFlowFunction getCallNoneToReturnFlowFunction(
			BasicBlockInContext<E> src, BasicBlockInContext<E> dest) {
		logger.debug("getNoneToReturnFunction");
		return union(new GlobalIdentityFunction<E>(domain),
				new CallNoneToReturnFunction<E>(domain));
	}

	@Override
	public IUnaryFlowFunction getCallToReturnFlowFunction(
			BasicBlockInContext<E> src, BasicBlockInContext<E> dest) {
		logger.debug("getCallToReturnFunction\n\t{}\n\t-> {}", src.getMethod().getSignature(), dest.getMethod().getSignature());
		return union(new GlobalIdentityFunction<E>(domain),
				new CallToReturnFunction<E>(domain));
	}

	@Override
	public IUnaryFlowFunction getNormalFlowFunction(BasicBlockInContext<E> src,
			BasicBlockInContext<E> dest) {
		List<UseDefPair> pairs = Lists.newArrayList();

		logger.debug("getNormalFlowFunction {}", dest.getMethod().getSignature());
		SSAInstruction inst = dest.getLastInstruction();
		SSAInstruction srcInst = src.getLastInstruction();
		if (null == inst && srcInst instanceof SSAReturnInstruction) {
			return specialCase(src);
		}
		if (null == inst ) {
			logger.debug("Using identity fn. for normal flow (inst==null) (src=={})", src.getLastInstruction());
			return IDENTITY_FN;
		}
		logger.debug("\tinstruction: {}", inst.toString());
		CGNode node = dest.getNode();

		Iterable<CodeElement> inCodeElts = getInCodeElts(node, inst);
		Iterable<CodeElement> outCodeElts = getOutCodeElts(node, inst);

		// for now, take the Cartesian product of the inputs and outputs:
		// TODO specialize this on a per-instruction basis to improve precision.
		for (CodeElement use : inCodeElts) {
			for (CodeElement def : outCodeElts) {
				pairs.add(new UseDefPair(use, def));
			}
		}

		// globals may be redefined here, so we can't union with the globals ID
		// flow function, as we often do elsewhere.
		return new PairBasedFlowFunction<E>(domain, pairs);
	}

	private IUnaryFlowFunction specialCase(BasicBlockInContext<E> src) {
		logger.warn("weird special case!");
		SSAReturnInstruction inst = (SSAReturnInstruction) src.getLastInstruction();
		CGNode node = src.getNode();
		Iterable<CodeElement> inCodeElts = getInCodeElts(node, inst);
		Iterable<CodeElement> outCodeElts = getOutCodeElts(node, inst);
		

		List<UseDefPair> pairs = Lists.newArrayList();
		for (CodeElement use : inCodeElts) {
			for (CodeElement def : outCodeElts) {
				pairs.add(new UseDefPair(use, def));
			}
		}

		// globals may be redefined here, so we can't union with the globals ID
		// flow function, as we often do elsewhere.
		return new PairBasedFlowFunction<E>(domain, pairs);		
	}

	@Override
    public IFlowFunction getReturnFlowFunction(
            BasicBlockInContext<E> call,
            BasicBlockInContext<E> src,
            BasicBlockInContext<E> dest) {
		logger.debug("getReturnFlowFunction\n\t{}\n\t-> {}\n\t-> {}", call.getNode().getMethod().getSignature(), src.getNode().getMethod().getSignature(), dest.getNode().getMethod().getSignature());

		// We need to map all uses in the return instruction (src) to these return values.
		SSAInstruction srcInst = src.getLastInstruction();
		// data flows from uses in src to dests in call, locals map to {}, globals pass through.
    	if (null == srcInst) {
    		logger.warn("null srcInst, {} pred nodes", graph.getPredNodeCount(src));
    		logger.warn("Using identity fn. for return flow (srcInst==null)");
    		return IDENTITY_FN;
    	}
    	
    	Iterable<CodeElement> returnedVals = getInCodeElts(src.getNode(), srcInst);

		// even if we don't know our dest, we want to flow into a return element in case this return is a sink
    	Iterable<CodeElement> baseReturn = Sets.newHashSet((CodeElement)new ReturnElement());
    	SSAInstruction destInst = dest.getLastInstruction();
    	if (null != destInst) {
    		// see if the return value is assigned to anything:
    		int callDefs = destInst.getNumberOfDefs();

    		if (0 == callDefs) {
    			logger.warn("No return defs");
    			// this situation should actually be handled normally by getOutCodeElts, 
    			// but I'm leaving the error msg here just in case

    			// nothing is returned, so no flows exist as a 
    			// result of this instruction. (no flows other than globals, that is)
    			// return new GlobalIdentityFunction<E>(domain);
    		}

    		Iterable<CodeElement> returnedLocs = getOutCodeElts(dest.getNode(), destInst);
    		return union(new GlobalIdentityFunction<E>(domain),
					 union(new ReturnFlowFunction<E>(domain, returnedVals, returnedLocs),
							 new ReturnFlowFunction<E>(domain, returnedVals, baseReturn)));
    	}
    	return union(new GlobalIdentityFunction<E>(domain),
    			 new ReturnFlowFunction<E>(domain, returnedVals, baseReturn));
    }

	private Iterable<CodeElement> getOutCodeElts(CGNode node,
			SSAInstruction inst) {
		int defNo = inst.getNumberOfDefs();
		Set<CodeElement> elts = Sets.newHashSet();

		if (inst instanceof SSAReturnInstruction) {
			// only one possible element for returns
			logger.debug("making a return element for {}", inst.toString());
			elts.add(new ReturnElement());
			return elts;
		}

		if (inst instanceof SSAPutInstruction) {
			// more work to do if we might be flowing out to a field
			final SSAPutInstruction put = (SSAPutInstruction) inst;
			final FieldReference fieldRef = put.getDeclaredField();
			final IField field = node.getClassHierarchy()
					.resolveField(fieldRef);
			PointerKey pk;
			if (put.isStatic()) {
				pk = pa.getHeapModel().getPointerKeyForStaticField(field);
			} else {
				pk = pa.getHeapModel().getPointerKeyForLocal(node,
						put.getUse(0));
			}
			for (InstanceKey ik : pa.getPointsToSet(pk)) {
				logger.debug("adding elements for field {} on {}",
						field.getName(), ik.getConcreteType().getName());
				elts.add(new FieldElement(ik, fieldRef, put.isStatic()));
				elts.add(new InstanceKeyElement(ik));
			}
		}

		for (int i = 0; i < defNo; i++) {
			int valNo = inst.getDef(i);

			elts.addAll(CodeElement.valueElements(pa, node, valNo));
		}

		return elts;
	}

	private Iterable<CodeElement> getInCodeElts(CGNode node, SSAInstruction inst) {
		int useNo = inst.getNumberOfUses();
		Set<CodeElement> elts = Sets.newHashSet();

		for (int i = 0; i < useNo; i++) {
			int valNo = inst.getUse(i);

			// Constants have valuenumber 0, which is otherwise, illegal.
			// these need to be skipped:
			if (0 == valNo) {
				continue;
			}
			try {
				elts.addAll(CodeElement.valueElements(pa, node, valNo));
			} catch (IllegalArgumentException e) {
				logger.error("Exception working on node: " + node);
				logger.error("Node is in method: " + node.getMethod());
				throw e;
			}
		}

		return elts;
	}

	// private Iterable<CodeElement> getOutCodeElts(final CGNode node, final
	// SSAInstruction inst) {
	// return new Iterable<CodeElement>() {
	// @Override
	// public Iterator<CodeElement> iterator() {
	// return new DefEltIterator(node, inst);
	// }
	// };
	// }
	//
	// private Iterable<CodeElement> getInCodeElts(final CGNode node, final
	// SSAInstruction inst) {
	// return new Iterable<CodeElement>() {
	// @Override
	// public Iterator<CodeElement> iterator() {
	// return new UseEltIterator(node, inst);
	// }
	// };
	// }

	private List<Set<CodeElement>> getOrdInCodeElts(CGNode node,
			SSAInstruction inst) {
		int useNo = inst.getNumberOfUses();
		List<Set<CodeElement>> elts = Lists.newArrayList();

		for (int i = 0; i < useNo; i++) {
			int valNo = inst.getUse(i);

			elts.add(CodeElement.valueElements(pa, node, valNo));
		}

		return elts;
	}

	private IUnaryFlowFunction union(final IUnaryFlowFunction g,
			final IUnaryFlowFunction h) {
		return new IUnaryFlowFunction() {
			@Override
			public IntSet getTargets(int d1) {
				return g.getTargets(d1).union(h.getTargets(d1));
			}
		};
	}
	/*
	 * private class UseEltIterator implements Iterator<CodeElement> { private
	 * int idx = 0; private Iterator<CodeElement> subIt; private final CGNode
	 * node; private final SSAInstruction inst; private final int count;
	 * 
	 * public UseEltIterator(CGNode node, SSAInstruction inst) { this.node =
	 * node; this.inst = inst; count = inst.getNumberOfUses();
	 * updateIterator(node, inst); }
	 * 
	 * private void updateIterator(final CGNode node, final SSAInstruction inst)
	 * { int valNo = inst.getUse(idx); idx++; Set<CodeElement> elements =
	 * CodeElement.valueElements(pa, node, valNo); subIt = elements.iterator();
	 * }
	 * 
	 * @Override public boolean hasNext() { if (subIt.hasNext()) { return true;
	 * } else if (idx < count) { updateIterator(node, inst); return hasNext(); }
	 * else { return false; } }
	 * 
	 * @Override public CodeElement next() { return subIt.next(); }
	 * 
	 * @Override public void remove() {} }
	 * 
	 * private class DefEltIterator implements Iterator<CodeElement> { private
	 * int idx = 0; private Iterator<CodeElement> subIt; private final CGNode
	 * node; private final SSAInstruction inst; private final int count;
	 * 
	 * public DefEltIterator(CGNode node, SSAInstruction inst) { this.node =
	 * node; this.inst = inst; count = inst.getNumberOfDefs();
	 * updateIterator(node, inst); }
	 * 
	 * private void updateIterator(final CGNode node, final SSAInstruction inst)
	 * { int valNo = inst.getDef(idx); idx++; Set<CodeElement> elements =
	 * CodeElement.valueElements(pa, node, valNo); subIt = elements.iterator();
	 * }
	 * 
	 * @Override public boolean hasNext() { if (subIt.hasNext()) { return true;
	 * } else if (idx < count) { updateIterator(node, inst); return hasNext(); }
	 * else { return false; } }
	 * 
	 * @Override public CodeElement next() { return subIt.next(); }
	 * 
	 * @Override public void remove() {} }
	 */
}
