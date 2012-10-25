package util;


import java.util.List;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.ibm.wala.dataflow.IFDS.IFlowFunction;
import com.ibm.wala.dataflow.IFDS.IFlowFunctionMap;
import com.ibm.wala.dataflow.IFDS.ISupergraph;
import com.ibm.wala.dataflow.IFDS.IUnaryFlowFunction;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.MutableSparseIntSet;

import domain.CodeElement;
import domain.DomainElement;
import domain.IFDSTaintDomain;
import domain.LocalElement;

public class TaintTransferFunctions <E extends ISSABasicBlock> implements
        IFlowFunctionMap<BasicBlockInContext<E>> {

    private final IFDSTaintDomain<E> domain;
    private final ISupergraph<BasicBlockInContext<E>,CGNode> graph;
    private final PointerAnalysis pa;

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
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public IUnaryFlowFunction getCallNoneToReturnFlowFunction(
            BasicBlockInContext<E> src,
            BasicBlockInContext<E> dest) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public IUnaryFlowFunction getCallToReturnFlowFunction(
            BasicBlockInContext<E> src,
            BasicBlockInContext<E> dest) {
    	return new CallToReturnFunction<E>(domain);
    }

    @Override
    public IUnaryFlowFunction getNormalFlowFunction(
            BasicBlockInContext<E> src,
            BasicBlockInContext<E> dest) {
    	List<UseDefPair> pairs = Lists.newArrayList();
    	
    	SSAInstruction inst = dest.getLastInstruction();
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
    	
        return new DefUse<E>(domain, pairs);
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

	@Override
    public IFlowFunction getReturnFlowFunction(
            BasicBlockInContext<E> call,
            BasicBlockInContext<E> src,
            BasicBlockInContext<E> dest) {
        // TODO Auto-generated method stub
        return null;
    }

}
