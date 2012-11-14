package org.scandroid.flow.functions;

import java.util.Map;
import java.util.Set;

import org.scandroid.domain.CodeElement;
import org.scandroid.domain.DomainElement;
import org.scandroid.domain.IFDSTaintDomain;
import org.scandroid.domain.InstanceKeyElement;
import org.scandroid.domain.LocalElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.ibm.wala.dataflow.IFDS.IUnaryFlowFunction;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.LocalPointerKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.MutableSparseIntSet;

/**
 * Propagates heap information from InstanceKeys to the LocalElements that point
 * to those keys
 * 
 * @author acfoltzer
 * 
 * @param <E>
 */
public class GlobalReturnToNodeFunction<E extends ISSABasicBlock> implements
		IUnaryFlowFunction {
	private static final Logger logger = LoggerFactory
			.getLogger(GlobalReturnToNodeFunction.class);

	private final IFDSTaintDomain<E> domain;
	private final Map<InstanceKey, Set<CodeElement>> ikMap;

	public GlobalReturnToNodeFunction(IFDSTaintDomain<E> domain,
			PointerAnalysis pa, CGNode node) {
		this.domain = domain;
		this.ikMap = Maps.newHashMap();
		for (PointerKey pk : pa.getPointerKeys()) {
			if (!(pk instanceof LocalPointerKey)) {
				continue;
			}
			LocalPointerKey lpk = (LocalPointerKey) pk;
			if (!lpk.getNode().equals(node)) {
				continue;
			}
			for (InstanceKey ik : pa.getPointsToSet(lpk)) {
				Set<CodeElement> elts = ikMap.get(ik);
				if (null == elts) {
					elts = Sets.newHashSet();
					ikMap.put(ik, elts);
				}
				elts.add(new LocalElement(lpk.getValueNumber()));
			}
		}
	}

	@Override
	public IntSet getTargets(int d) {
		MutableSparseIntSet set = MutableSparseIntSet.makeEmpty();
		if (0 == d) {
			set.add(d);
		} else {
			DomainElement de = domain.getMappedObject(d);
			if (de.codeElement instanceof InstanceKeyElement) {
				InstanceKey ik = ((InstanceKeyElement) de.codeElement)
						.getInstanceKey();
				Set<CodeElement> elts = ikMap.get(ik);
				if (null != elts) {
					for (CodeElement elt : elts) {
						set.add(domain.getMappedIndex(new DomainElement(elt,
								de.taintSource)));
					}
				}
			} else {
				logger.debug("throwing away {}", de);
			}
		}
		return set;
	}

}
