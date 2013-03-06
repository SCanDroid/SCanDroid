/**
 * 
 */
package com.ibm.wala.ipa.callgraph.impl;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.SSAArrayStoreInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.TypeReference;

/**
 * An entrypoint implementation that generates allocations for arguments, and
 * then allocates values for those arguments' fields, recursively
 * 
 * @author acfoltzer
 * 
 */
public class FieldPopulatingEntrypoint extends DefaultEntrypoint {
	private static final Logger logger = LoggerFactory
			.getLogger(FieldPopulatingEntrypoint.class);

	public FieldPopulatingEntrypoint(IMethod method, IClassHierarchy cha) {
		super(method, cha);
	}

	@Override
	protected int makeArgument(AbstractRootMethod m, int i) {
		TypeReference[] paramTypes = getParameterTypes(i);
		if (paramTypes.length != 1) {
			throw new IllegalArgumentException(
					"more than one possible entrypoint argument type");
		}
		TypeReference paramType = paramTypes[0];

		final Map<TypeReference, Integer> env = Maps.newHashMap();
		return makeArgumentRec(m, paramType, env);
	}

	private int makeArgumentRec(AbstractRootMethod m, TypeReference paramType,
			Map<TypeReference, Integer> env) {
		if (null == paramType) {
			return -1;
		}

		// primitives are easy
		if (paramType.isPrimitiveType()) {
			return m.addLocal();
		}

		// otherwise check whether we've already seen this type
		Integer v = env.get(paramType);
		if (null != v) {
			return v;
		}

		IClass clazz = getCha().lookupClass(paramType);
		if (null == clazz) {
			logger.warn("couldn't resolve entrypoint argument class {}", paramType);
			return m.addLocal();
		}		
		
		// and if we haven't, now let's allocate it and get started
		v = m.addAllocation(paramType).getDef();
		env.put(paramType, v);
		
		if (clazz.isArrayClass()) {
			// recur on array elements 
			final TypeReference eType = paramType.getArrayElementType();
			int e = makeArgumentRec(m, eType, env);
			addArrayStore(m, v, m.getValueNumberForIntConstant(0), e, eType);
		} else {
			// recur on all fields
			for (IField field : clazz.getAllFields()) {
				final FieldReference fieldRef = field.getReference();
				TypeReference fieldType = field.getFieldTypeReference();
				int fv = makeArgumentRec(m, fieldType, env);				
				if (field.isStatic()) {
					addPutStatic(m, fieldRef, fv);
				} else {
					addPutInstance(m, fieldRef, v, fv);
				}
			}
		}

		return v;
	}

	public SSAPutInstruction addPutInstance(AbstractRootMethod m,
			FieldReference ref, int object, int value) {
		final SSAPutInstruction result = m.insts.PutInstruction(object, value,
				ref);
		m.statements.add(result);
		m.cache.invalidate(m, Everywhere.EVERYWHERE);
		return result;
	}

	public SSAPutInstruction addPutStatic(AbstractRootMethod m,
			FieldReference ref, int value) {
		final SSAPutInstruction result = m.insts.PutInstruction(value, ref);
		m.statements.add(result);
		m.cache.invalidate(m, Everywhere.EVERYWHERE);
		return result;
	}

	public SSAArrayStoreInstruction addArrayStore(AbstractRootMethod m,
			int arrayRef, int idx, int value, TypeReference type) {
		final SSAArrayStoreInstruction result = m.insts.ArrayStoreInstruction(
				arrayRef, idx, value, type);
		m.statements.add(result);
		m.cache.invalidate(m, Everywhere.EVERYWHERE);
		return result;
	}
}
