package util;


import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.ibm.wala.dataflow.IFDS.IFlowFunction;
import com.ibm.wala.dataflow.IFDS.IFlowFunctionMap;
import com.ibm.wala.dataflow.IFDS.IReversibleFlowFunction;
import com.ibm.wala.dataflow.IFDS.ISupergraph;
import com.ibm.wala.dataflow.IFDS.IUnaryFlowFunction;
import com.ibm.wala.dataflow.IFDS.IdentityFlowFunction;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.SparseIntSet;

import domain.CodeElement;
import domain.IFDSTaintDomain;

public class TaintTransferFunctions <E extends ISSABasicBlock> implements
        IFlowFunctionMap<BasicBlockInContext<E>> {
	@SuppressWarnings("unused")
	private static final Logger logger = 
			LoggerFactory.getLogger(TaintTransferFunctions.class);
	
    private final IFDSTaintDomain<E> domain;
    private final ISupergraph<BasicBlockInContext<E>,CGNode> graph;
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
    public IUnaryFlowFunction getCallFlowFunction(
            BasicBlockInContext<E> src,
            BasicBlockInContext<E> dest,
            BasicBlockInContext<E> ret) {
    	SSAInstruction srcInst= src.getLastInstruction();
    	if (null == srcInst) {
    		return IDENTITY_FN;
    	}
    	SSAInstruction destInst = dest.getLastInstruction();
    	if (null == destInst) {
    		return IDENTITY_FN;
    	}
    	
    	CGNode node = src.getNode();
    	// each use in an invoke instruction is a parameter to the invoked method,
    	// these are the uses:
    	List<Set<CodeElement>> actualParams = getOrdInCodeElts(node, srcInst);
    	List<Set<CodeElement>> formalParams = getOrdInCodeElts(node, destInst);

        return union(new GlobalIdenityFunction<E>(domain),
        			 new CallFlowFunction<E>(domain, actualParams, formalParams));
    }
    
    @Override
    public IUnaryFlowFunction getCallNoneToReturnFlowFunction(
            BasicBlockInContext<E> src,
            BasicBlockInContext<E> dest) {
        return union(new GlobalIdenityFunction<E>(domain),
			         new CallNoneToReturnFunction<E>(domain));
    }

    @Override
    public IUnaryFlowFunction getCallToReturnFlowFunction(
            BasicBlockInContext<E> src,
            BasicBlockInContext<E> dest) {
    	return union(new GlobalIdenityFunction<E>(domain),
    			     new CallToReturnFunction<E>(domain));
    }

    @Override
    public IUnaryFlowFunction getNormalFlowFunction(
            BasicBlockInContext<E> src,
            BasicBlockInContext<E> dest) {
    	List<UseDefPair> pairs = Lists.newArrayList();
    	
    	SSAInstruction inst = dest.getLastInstruction();
    	if (null == inst) {
    		return IDENTITY_FN;
    	}
    	CGNode node = dest.getNode();
    	
    	Set<CodeElement> inCodeElts  = getInCodeElts(node, inst);
    	Set<CodeElement> outCodeElts = getOutCodeElts(node, inst);
    	
    	// for now, take the Cartesian product of the inputs and outputs:
    	// TODO specialize this on a per-instruction basis to improve precision.
    	for (CodeElement use : inCodeElts) {
			for(CodeElement def : outCodeElts) {
				pairs.add(new UseDefPair(use, def));
			}
		}
    	
        return union(new GlobalIdenityFunction<E>(domain),
        		     new PairBasedFlowFunction<E>(domain, pairs));
    }

	@Override
    public IFlowFunction getReturnFlowFunction(
            BasicBlockInContext<E> call,
            BasicBlockInContext<E> src,
            BasicBlockInContext<E> dest) {
		// data flows from uses in src to dests in call, locals map to {}, globals pass through.
    	SSAInstruction callInst = call.getLastInstruction();
    	if (null == callInst) {
    		return IDENTITY_FN;
    	}
    	
		// see if the return vaule is assigned to anything:
		int callDefs = callInst.getNumberOfDefs();

		if (0 == callDefs) {
			// nothing is returned, so no flows exist as a 
			// result of this instruction. (no flows other than globals, that is)
			return new GlobalIdenityFunction<E>(domain);
		}

		// Ok - there was a return val (or multiple...) so we need to map
		// all uses in the return instruction (src) to these return values.
		SSAInstruction srcInst = src.getLastInstruction();

    	if (null == srcInst) {
    		return IDENTITY_FN;
    	}
    	
		Set<CodeElement> returnedVals = getInCodeElts(src.getNode(), srcInst);
		Set<CodeElement> returnedLocs = getOutCodeElts(call.getNode(), callInst);
		
		return union(new GlobalIdenityFunction<E>(domain),
					 new ReturnFlowFunction<E>(domain, returnedVals, returnedLocs));
    }


    private Set<CodeElement> getOutCodeElts(CGNode node, SSAInstruction inst) {
    	int defNo = inst.getNumberOfDefs();
    	Set<CodeElement> elts = Sets.newHashSet();
    	
    	for (int i =0; i < defNo; i++) {
    		int valNo = inst.getDef(i);
    		
    		elts.addAll(CodeElement.valueElements(pa, node, valNo));
    	}
    	
    	return elts;
	}

	private Set<CodeElement> getInCodeElts(CGNode node, SSAInstruction inst) {
    	int useNo = inst.getNumberOfUses();
    	Set<CodeElement> elts = Sets.newHashSet();
    	
    	for (int i =0; i < useNo; i++) {
    		int valNo = inst.getUse(i);
    		
    		elts.addAll(CodeElement.valueElements(pa, node, valNo));
    	}
    	
    	return elts;
	}


	private List<Set<CodeElement>> getOrdInCodeElts(CGNode node, SSAInstruction inst) {
    	int useNo = inst.getNumberOfUses();
    	List<Set<CodeElement>> elts = Lists.newArrayList();
    	
    	for (int i =0; i < useNo; i++) {
    		int valNo = inst.getUse(i);
    		
    		elts.add(CodeElement.valueElements(pa, node, valNo));
    	}
    	
    	return elts;
	}
	

	private IUnaryFlowFunction union(final IUnaryFlowFunction g, final IUnaryFlowFunction h) {
		return new IUnaryFlowFunction() {
			@Override
			public IntSet getTargets(int d1) {
				return g.getTargets(d1).union(h.getTargets(d1));
			}
		};
	}
}
