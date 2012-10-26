/**
 * 
 */
package util;

import com.ibm.wala.dataflow.IFDS.IUnaryFlowFunction;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.SparseIntSet;

import domain.DomainElement;
import domain.IFDSTaintDomain;
import domain.LocalElement;

/**
 * Flow function that only permits globals  - and the zero element - to flow through
 * 
 * @author creswick
 *
 */
public class GlobalIdenityFunction <E extends ISSABasicBlock>
    implements IUnaryFlowFunction {
	
	private final IFDSTaintDomain<E> domain;

	public GlobalIdenityFunction(IFDSTaintDomain<E> domain) {
		this.domain = domain;
	}

	/* (non-Javadoc)
	 * @see com.ibm.wala.dataflow.IFDS.IUnaryFlowFunction#getTargets(int)
	 */
	@Override
	public IntSet getTargets(int d1) {
		if (0 == d1) {
			return TaintTransferFunctions.ZERO_SET;
		}
		
		DomainElement de = domain.getMappedObject(d1);
		if( de.codeElement instanceof LocalElement ) {
			// if the query domain element is a local, then it is /not/ passed through.
			return TaintTransferFunctions.EMPTY_SET;
		} else {
			return SparseIntSet.singleton(d1);
		}
	}
}
