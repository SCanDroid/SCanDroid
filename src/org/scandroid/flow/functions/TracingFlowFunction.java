package org.scandroid.flow.functions;

import org.scandroid.domain.IFDSTaintDomain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ibm.wala.dataflow.IFDS.IUnaryFlowFunction;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.IntSetAction;

public class TracingFlowFunction<E extends ISSABasicBlock> implements IUnaryFlowFunction {	
	private final IFDSTaintDomain<E> domain;
	private final IUnaryFlowFunction function;
	private final Logger logger;
	
	public TracingFlowFunction(IFDSTaintDomain<E> domain, IUnaryFlowFunction function) {
		this.domain = domain;
		this.function = function;
		this.logger = LoggerFactory.getLogger(function.getClass());
	}
	
	@Override
	public IntSet getTargets(int d1) {
		IntSet result = function.getTargets(d1); 
		logger.debug("TRACING: {}", domain.getMappedObject(d1));
		result.foreach(new IntSetAction() {
			
			@Override
			public void act(int x) {
				logger.debug("\t{}", domain.getMappedObject(x));
			}
		});
		return result;
	}

}
