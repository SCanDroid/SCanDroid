package util;

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
            PointerAnalysis pa)
    {
        this.domain = domain;
        this.graph = graph;
        this.pa = pa;
    }

    @Override
    public IUnaryFlowFunction getCallFlowFunction(BasicBlockInContext<E> arg0,
            BasicBlockInContext<E> arg1, BasicBlockInContext<E> arg2) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public IUnaryFlowFunction getCallNoneToReturnFlowFunction(
            BasicBlockInContext<E> arg0, BasicBlockInContext<E> arg1) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public IUnaryFlowFunction getCallToReturnFlowFunction(
            BasicBlockInContext<E> arg0, BasicBlockInContext<E> arg1) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public IUnaryFlowFunction getNormalFlowFunction(
            BasicBlockInContext<E> arg0, BasicBlockInContext<E> arg1) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public IFlowFunction getReturnFlowFunction(BasicBlockInContext<E> arg0,
            BasicBlockInContext<E> arg1, BasicBlockInContext<E> arg2) {
        // TODO Auto-generated method stub
        return null;
    }

}
