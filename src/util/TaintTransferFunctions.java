package util;


import com.google.common.collect.Lists;
import com.ibm.wala.dataflow.IFDS.IFlowFunction;
import com.ibm.wala.dataflow.IFDS.IFlowFunctionMap;
import com.ibm.wala.dataflow.IFDS.ISupergraph;
import com.ibm.wala.dataflow.IFDS.IUnaryFlowFunction;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ssa.ISSABasicBlock;

import domain.IFDSTaintDomain;

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
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public IUnaryFlowFunction getNormalFlowFunction(
            BasicBlockInContext<E> src,
            BasicBlockInContext<E> dest) {
        // TODO Auto-generated method stub
        return null;
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
