/**
 * 
 */
package org.scandroid.spec;

import java.util.Collection;

import org.scandroid.flow.types.FieldFlow;
import org.scandroid.flow.types.FlowType;

import com.google.common.collect.Sets;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ssa.ISSABasicBlock;

/**
 * @author acfoltzer
 *
 */
public class StaticFieldSinkSpec extends SinkSpec {

	private final IField field;	
	private final IMethod method;

	/**
	 * @param field to check for flows
	 * @param method to check for flow (at method's exit), e.g., main
	 */
	public StaticFieldSinkSpec(IField field, IMethod method) {
		this.field = field;
		this.method = method;
	}
	
	/* (non-Javadoc)
	 * @see org.scandroid.spec.SinkSpec#getFlowType(com.ibm.wala.ipa.cfg.BasicBlockInContext)
	 */
	@Override
	public <E extends ISSABasicBlock> Collection<FlowType<E>> getFlowType(
			BasicBlockInContext<E> block) {
		@SuppressWarnings("unchecked")
		Collection<FlowType<E>> flow = Sets.newHashSet((FlowType<E>) new FieldFlow<E>(block, field, false));
		return flow;
	}

	@Override
	public String toString() {		
		return String.format("StaticFieldSinkSpec(%s)", field);
	}
	
	public IField getField() {
		return field;
	}
	
	public IMethod getMethod() {
		return method;
	}
}
