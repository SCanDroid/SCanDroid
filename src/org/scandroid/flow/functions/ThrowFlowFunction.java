package org.scandroid.flow.functions;

import org.scandroid.domain.CodeElement;
import org.scandroid.domain.DomainElement;
import org.scandroid.domain.IFDSTaintDomain;
import org.scandroid.domain.LocalElement;
import org.scandroid.domain.ReturnElement;
import org.scandroid.domain.ThrowElement;

import com.ibm.wala.dataflow.IFDS.IUnaryFlowFunction;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.SparseIntSet;

public class ThrowFlowFunction<E extends ISSABasicBlock> implements 
        IUnaryFlowFunction {

	private final IFDSTaintDomain<E> domain;
	private final CodeElement ce;

	/**
	 * @param domain
	 * @param def
	 *            of the exception the invoke instruction throws
	 */
	public ThrowFlowFunction(IFDSTaintDomain<E> domain, int def) {
		this.domain = domain;
		this.ce = new LocalElement(def);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.ibm.wala.dataflow.IFDS.IUnaryFlowFunction#getTargets(int)
	 */
	@Override
	public IntSet getTargets(int d1) {
		if (0 == d1) {
			return TaintTransferFunctions.ZERO_SET;
		}

		DomainElement de = domain.getMappedObject(d1);
		// if the domain element is a return element, propagate its taint
		if (de.codeElement instanceof ThrowElement) {
			return SparseIntSet.singleton(domain
					.getMappedIndex(new DomainElement(ce, de.taintSource)));
		}
		return TaintTransferFunctions.EMPTY_SET;
	}

}
