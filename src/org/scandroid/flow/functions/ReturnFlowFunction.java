/**
 * 
 */
package org.scandroid.flow.functions;

import org.scandroid.domain.CodeElement;
import org.scandroid.domain.DomainElement;
import org.scandroid.domain.IFDSTaintDomain;

import com.ibm.wala.dataflow.IFDS.IUnaryFlowFunction;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.MutableSparseIntSet;


/**
 * @author creswick
 *
 */
public class ReturnFlowFunction <E extends ISSABasicBlock> implements IUnaryFlowFunction {

	private final IFDSTaintDomain<E> domain;
	private final Iterable<CodeElement> returnedVals;
	private final Iterable<CodeElement> returnedLocs;

	public ReturnFlowFunction(IFDSTaintDomain<E> domain,
			Iterable<CodeElement> returnedVals, Iterable<CodeElement> returnedLocs) {
		this.domain = domain;
		this.returnedVals = returnedVals;
		this.returnedLocs = returnedLocs;
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
        MutableSparseIntSet set = MutableSparseIntSet.makeEmpty();
		
        for (CodeElement ce : returnedVals) {
			if (!de.codeElement.equals(ce)) {
				continue;
			}
			
			for (CodeElement destCe : returnedLocs) {
				DomainElement newDe = new DomainElement(destCe, de.taintSource);
				set.add(domain.getMappedIndex(newDe));
			}
		}
		
		return set;
	}

}
