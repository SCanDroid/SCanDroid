/**
 * 
 */
package org.scandroid.flow.functions;

import java.util.Set;

import org.scandroid.domain.DomainElement;
import org.scandroid.domain.IFDSTaintDomain;

import com.ibm.wala.dataflow.IFDS.IUnaryFlowFunction;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.MutableSparseIntSet;
import com.ibm.wala.util.intset.SparseIntSet;

/**
 * A flow function which maps the zero fact to a set of new dataflow facts,
 * essentially introducing them from nothing. Identity for all other facts.
 * 
 * @author acfoltzer
 * 
 */
public class ConstantFlowFunction <E extends ISSABasicBlock> implements IUnaryFlowFunction {
	private final MutableSparseIntSet result;
	
	public ConstantFlowFunction(IFDSTaintDomain<E> domain, Set<DomainElement> elts) {
		result = MutableSparseIntSet.make(TaintTransferFunctions.ZERO_SET);
		for (DomainElement de : elts) {
			result.add(domain.getMappedIndex(de));
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.ibm.wala.dataflow.IFDS.IUnaryFlowFunction#getTargets(int)
	 */
	@Override
	public IntSet getTargets(int d1) {
		return 0 == d1 ? result : SparseIntSet.singleton(d1);
	}

}
