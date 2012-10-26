package util;

import com.ibm.wala.dataflow.IFDS.IUnaryFlowFunction;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.MutableSparseIntSet;

import domain.CodeElement;
import domain.DomainElement;
import domain.IFDSTaintDomain;
import flow.types.FlowType;

public final class CallNoneToReturnFunction <E extends ISSABasicBlock> implements IUnaryFlowFunction {
	private IFDSTaintDomain<E> domain;

	public CallNoneToReturnFunction(IFDSTaintDomain<E> domain) {
		this.domain = domain;
	}

	@Override
	public IntSet getTargets(int d) {
	    MutableSparseIntSet set = MutableSparseIntSet.makeEmpty();
		if (0 == d) {
			set.add(0);
			return set;
		}
		
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