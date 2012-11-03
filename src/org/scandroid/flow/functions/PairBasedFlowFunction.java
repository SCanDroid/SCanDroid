package org.scandroid.flow.functions;

import java.util.List;

import org.scandroid.domain.CodeElement;
import org.scandroid.domain.DomainElement;
import org.scandroid.domain.IFDSTaintDomain;

import com.ibm.wala.dataflow.IFDS.IUnaryFlowFunction;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.MutableSparseIntSet;


final class PairBasedFlowFunction <E extends ISSABasicBlock> implements IUnaryFlowFunction {
    private final List<UseDefPair> useToDefList;
	private final IFDSTaintDomain<E> domain;

    public PairBasedFlowFunction(IFDSTaintDomain<E> domain, List<UseDefPair> useToDefList) {
    	this.domain = domain;
        this.useToDefList = useToDefList;
    }
    
    @Override
    public IntSet getTargets(int d) {
    	if (0 == d) {
    		return TaintTransferFunctions.ZERO_SET;
    	}
    	
        MutableSparseIntSet set = MutableSparseIntSet.makeEmpty();

        DomainElement de = domain.getMappedObject(d);
        // Here we list what facts we pass through. If a fact was true
        // before executing this instruction, it'll be true after,
        // unless we created a new definition of its associated
        // CodeElement.
    	
    	// see if D is still true; if so, pass it through:
    	// (this corresponds to the vertical 'pass through' arrows in the RHS paper)
    	// we actually assume that D passes through, unless there 
    	// is evidence to the contrary.  Because of this, instructions will
    	// 'default' to propagating taints that were not relevant to that 
    	// instruction, which is what we want.
    	set.add(d);
    	for (UseDefPair udPair : useToDefList) {
			CodeElement def = udPair.getDef();
			
			if (def.equals(de.codeElement)) {
				// this instruction redefined D, so we 
				// do *not* pass it through - this conditional has 
				// contradicted our assumption that D should be passed through,
				// so remove it from the set:
				set.remove(d);
				break;
			}
		}
    	
    	////////////////////////////////////////////////////////////////
    	// see if the taints associated with D also flow through to any 
    	// other domain elements:
    	
    	for (UseDefPair udPair : useToDefList) {
			CodeElement use = udPair.getUse();
			
			if (use.equals(de.codeElement)) {
				// ok, the d element flows to the def, so we add that def
				// and keep looking.
				DomainElement newDE = 
				     new DomainElement(udPair.getDef(), de.taintSource);
				set.add(domain.getMappedIndex(newDE));
			}
    	}	
        return set;
    }
}