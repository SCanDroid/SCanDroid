package org.scandroid.flow.functions;

import org.scandroid.domain.CodeElement;
import org.scandroid.domain.DomainElement;
import org.scandroid.domain.IFDSTaintDomain;
import org.scandroid.flow.types.FlowType;

import com.ibm.wala.dataflow.IFDS.IUnaryFlowFunction;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.MutableSparseIntSet;


public final class CallNoneToReturnFunction <E extends ISSABasicBlock> implements IUnaryFlowFunction {
	private IFDSTaintDomain<E> domain;

	public CallNoneToReturnFunction(IFDSTaintDomain<E> domain) {
		this.domain = domain;
	}

	@Override
	public IntSet getTargets(int d) {
		if (0 == d) {
			return TaintTransferFunctions.ZERO_SET;
		}
		
	    MutableSparseIntSet set = MutableSparseIntSet.makeEmpty();
		// We don't know anything about the function called,
		// so we have to make some assumptions.  The safest assumption
		// is that everything goes to everything:
	    
	    // this effectively taints everything in the heap that we've seen before.
		DomainElement de = domain.getMappedObject(d);
		
		FlowType<E> taint = de.taintSource;
		
		for (CodeElement ce : domain.codeElements() ){
			int elt = domain.getMappedIndex(new DomainElement(ce, taint));
			set.add(elt);
		}
		return set;
	}
}