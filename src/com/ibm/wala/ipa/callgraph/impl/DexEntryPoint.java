package com.ibm.wala.ipa.callgraph.impl;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;


public class DexEntryPoint extends DefaultEntrypoint {

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
			System.out.println("trA: " + tr);
		return trA;
	}

}
