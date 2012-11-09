package org.scandroid.flow.functions;


import java.util.List;
import java.util.Set;

import org.scandroid.domain.CodeElement;
import org.scandroid.domain.IFDSTaintDomain;
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


public class IDTransferFunctions <E extends ISSABasicBlock> implements
        IFlowFunctionMap<BasicBlockInContext<E>> {
	@SuppressWarnings("unused")
	private static final Logger logger = 
			LoggerFactory.getLogger(IDTransferFunctions.class);
	
	public static final IntSet EMPTY_SET = new SparseIntSet();
	public static final IntSet ZERO_SET = SparseIntSet.singleton(0);

    private static final IReversibleFlowFunction IDENTITY_FN = new IdentityFlowFunction();
    
    public IDTransferFunctions(IFDSTaintDomain<E> domain,
            ISupergraph<BasicBlockInContext<E>, CGNode> graph, 
            PointerAnalysis pa) {
    }

	@Override
	public IUnaryFlowFunction getNormalFlowFunction(BasicBlockInContext<E> src,
			BasicBlockInContext<E> dest) {
		return IDENTITY_FN;
	}

	@Override
	public IUnaryFlowFunction getCallFlowFunction(BasicBlockInContext<E> src,
			BasicBlockInContext<E> dest, BasicBlockInContext<E> ret) {
		return IDENTITY_FN;
	}

	@Override
	public IFlowFunction getReturnFlowFunction(BasicBlockInContext<E> call,
			BasicBlockInContext<E> src, BasicBlockInContext<E> dest) {
		return IDENTITY_FN;
	}

	@Override
	public IUnaryFlowFunction getCallToReturnFlowFunction(
			BasicBlockInContext<E> src, BasicBlockInContext<E> dest) {
		return IDENTITY_FN;
	}

	@Override
	public IUnaryFlowFunction getCallNoneToReturnFlowFunction(
			BasicBlockInContext<E> src, BasicBlockInContext<E> dest) {
		return IDENTITY_FN;
	}

}
