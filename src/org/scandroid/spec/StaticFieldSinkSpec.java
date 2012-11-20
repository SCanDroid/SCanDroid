/**
 * 
 */
package org.scandroid.spec;

import java.util.Collection;

import org.scandroid.flow.types.FieldFlow;
import org.scandroid.flow.types.FlowType;

import com.google.common.collect.Sets;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ssa.ISSABasicBlock;

/**
 * @author acfoltzer
 *
 */
public class StaticFieldSinkSpec extends SinkSpec {

	private final IField field;	

	public StaticFieldSinkSpec(IField field) {
		this.field = field;
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
}
