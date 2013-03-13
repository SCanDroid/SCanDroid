package org.scandroid.flow.types;

import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAInvokeInstruction;

public class ExceptionFlow <E extends ISSABasicBlock> extends FlowType<E> {

	public ExceptionFlow(BasicBlockInContext<E> block, boolean source) {
		super(block, source);
	}
	
	@Override
	public String toString() {
		return "ExceptionFlow( " + super.toString() + ")";
	}

    @Override
    public String descString() {
    	return "exception";
    }
    
	@Override
	public <R> R visit(FlowTypeVisitor<E, R> v) {
		return v.visitExceptionFlow(this);
	}
	
}
