package org.scandroid.spec;

import java.util.Collection;
import java.util.HashSet;

import org.scandroid.flow.types.ExceptionFlow;
import org.scandroid.flow.types.FlowType;

import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ssa.ISSABasicBlock;

public class EntryExcSinkSpec extends SinkSpec {

	public EntryExcSinkSpec(MethodNamePattern name) {
		namePattern = name;
	}

	@Override
	public <E extends ISSABasicBlock> Collection<FlowType<E>> getFlowType(
			BasicBlockInContext<E> block) {
		HashSet<FlowType<E>> flowSet = new HashSet<FlowType<E>>();
		flowSet.clear();
		flowSet.add(new ExceptionFlow<E>(block, false));
		return flowSet;
	}

	
	@Override
	public String toString() {
		return String.format("EntryExcSinkSpec(%s)",
				namePattern.getDescriptor());
	}
	
}
