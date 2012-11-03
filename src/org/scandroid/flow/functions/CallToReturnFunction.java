package org.scandroid.flow.functions;

import org.scandroid.domain.DomainElement;
import org.scandroid.domain.IFDSTaintDomain;
import org.scandroid.domain.LocalElement;

import com.ibm.wala.dataflow.IFDS.IUnaryFlowFunction;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.MutableSparseIntSet;


public class CallToReturnFunction <E extends ISSABasicBlock> 
    implements IUnaryFlowFunction {

	private IFDSTaintDomain<E> domain;

	public CallToReturnFunction(IFDSTaintDomain<E> domain) {
		this.domain = domain;
	}

	@Override
	public IntSet getTargets(int d) {
		MutableSparseIntSet set = MutableSparseIntSet.makeEmpty();
        
		// Local elements (and the 0 element) flow through CallToReturn edges, 
		// but nothing else does (everything else is subject to whatever 
		// happened in the invoked function)
        if (0 == d) {
        	set.add(d);
        } else {
        	DomainElement de = domain.getMappedObject(d);
        	if (de.codeElement instanceof LocalElement) {
        		set.add(d);
        	}
        }
		return set;
	}

}
