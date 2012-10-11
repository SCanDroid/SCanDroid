package com.ibm.wala.ipa.callgraph.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;


public class DexEntryPoint extends DefaultEntrypoint {
	private static final Logger logger = LoggerFactory.getLogger(DexEntryPoint.class);

	public DexEntryPoint(IMethod method, IClassHierarchy cha) {
		super(method, cha);
		// TODO Auto-generated constructor stub
	}

	public DexEntryPoint(MethodReference method, IClassHierarchy cha) {
		super(method, cha);
		// TODO Auto-generated constructor stub
	}

	@Override
	protected TypeReference[] makeParameterTypes(IMethod method, int i) {
		TypeReference[] trA = new TypeReference[] {method.getParameterType(i)};
		for (TypeReference tr:trA)
			logger.trace("trA: " + tr);
		return trA;
	}

}
