package com.ibm.wala.ipa.callgraph.impl;

import java.util.ArrayList;
import java.util.Arrays;

import com.ibm.wala.cfg.IBasicBlock;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.shrikeBT.IInvokeInstruction;
import com.ibm.wala.ssa.SSAArrayStoreInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.Descriptor;
import com.ibm.wala.types.MemberReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.strings.Atom;
import com.ibm.wala.util.warnings.Warning;
import com.ibm.wala.util.warnings.Warnings;

public class DexFakeRootMethod extends AbstractRootMethod {

	public static final Atom name = Atom.findOrCreateAsciiAtom("DexFakeRootMethod");

	public static final Descriptor descr = Descriptor.findOrCreate(new TypeName[0], TypeReference.VoidName);

	public static final MethodReference rootMethod = MethodReference.findOrCreate(FakeRootClass.FAKE_ROOT_CLASS, name, descr);

	public DexFakeRootMethod(final IClassHierarchy cha, AnalysisOptions options, AnalysisCache cache) {
		super(rootMethod, cha, options, cache);
	}

	@Override
	public SSANewInstruction addAllocation(TypeReference T) {
		return addAllocation(T, true);
	}

	private SSANewInstruction addAllocation(TypeReference T, boolean invokeCtor) {
		if (T == null) {
			throw new IllegalArgumentException("T is null");
		}
		int instance = nextLocal++;
		SSANewInstruction result = null;
		if (T.isReferenceType()) {
			NewSiteReference ref = NewSiteReference.make(statements.size(), T);
			if (T.isArrayType()) {
				int[] sizes = new int[T.getDimensionality()];
				Arrays.fill(sizes, getValueNumberForIntConstant(1));
				result = insts.NewInstruction(instance, ref, sizes);
			} else {
				result = insts.NewInstruction(instance, ref);
			}
			statements.add(result);

			IClass klass = cha.lookupClass(T);
			if (klass == null) {
				Warnings.add(AllocationFailure.create(T));
				return null;
			}

			if (klass.isArrayClass()) {
				int arrayRef = result.getDef();
				TypeReference e = klass.getReference().getArrayElementType();
				while (e != null && !e.isPrimitiveType()) {
					// allocate an instance for the array contents
					NewSiteReference n = NewSiteReference.make(statements.size(), e);
					int alloc = nextLocal++;
					SSANewInstruction ni = null;
					if (e.isArrayType()) {
						int[] sizes = new int[T.getDimensionality()];
						Arrays.fill(sizes, getValueNumberForIntConstant(1));
						ni = insts.NewInstruction(alloc, n, sizes);
					} else {
						ni = insts.NewInstruction(alloc, n);
					}
					statements.add(ni);

					// emit an astore
					SSAArrayStoreInstruction store = insts.ArrayStoreInstruction(arrayRef, getValueNumberForIntConstant(0), alloc, e);
					statements.add(store);

					e = e.isArrayType() ? e.getArrayElementType() : null;
					arrayRef = alloc;
				}
			}
			if (invokeCtor) {
				IMethod ctor = cha.resolveMethod(klass, MethodReference.initSelector);
				System.out.println("ctor: " + ctor.getSignature());
				System.out.println("klass: " + klass.getName());
				
				if (ctor!=null) {
					ArrayList<SSANewInstruction> allocSitesAL = new ArrayList<SSANewInstruction>();

					if (!ctor.getDeclaringClass().getName().toString().equals(klass.getName().toString())) {
						System.out.println("klass != ctor: " + ctor.getSignature());
						boolean found = false;
						for (IMethod im: klass.getAllMethods()) {
							if (im.getDeclaringClass().getName().toString().equals(klass.getName().toString()) && 
									im.getSelector().getName().toString().equals(MethodReference.initAtom.toString())) {
								ctor = im;
								System.out.println("new ctor: " + ctor.getSignature());
								for (int j = 1; j < im.getNumberOfParameters(); j++) {									
									allocSitesAL.add(addAllocation(im.getParameterType(j), invokeCtor));
								}
								found = true;
								break;
							}
						}
						if (!found) {
							System.out.println("no constructors.  klass may be an interface: " + klass.isInterface());
//							//for (IClass ic:cha.computeSubClasses(T)) {
//							for (IClass ic: cha.getImplementors(T)){
//								System.out.println("\timplementors: " + ic.getName());
//								if (ic.getClassLoader().getReference().equals(ClassLoaderReference.Application)) {
//									System.out.println("\tApplication implementor: " + ic.getName());
//									addAllocation(ic.getReference(), invokeCtor);
//									break;
//								}
//								else if (ic.getName().toString().equals("Landroid/location/ILocationManager$Stub$Proxy")) {
//									System.out.println("\tILocationManager implementor: " + ic.getName());
//									addAllocation(ic.getReference(), invokeCtor);
//									break;
//								}
//								else if (ic.getName().toString().equals("Landroid/os/Binder")) {
//									System.out.println("\tIBinder implementor: " + ic.getName());
//									addAllocation(ic.getReference(), invokeCtor);
//									break;
//								}
//							
//							}
						}
					}
					int[] allocSites = new int[ctor.getNumberOfParameters()];
					allocSites[0] = instance;
					assert(allocSites.length-1 == allocSitesAL.size());
					for (int i = 1; i < allocSites.length; i++) {
						allocSites[i] = allocSitesAL.get(i-1).getDef();
					}
					addInvocation(allocSites, CallSiteReference.make(statements.size(), ctor.getReference(),
							IInvokeInstruction.Dispatch.SPECIAL));
				}
			}
		}
		cache.invalidate(this, Everywhere.EVERYWHERE);
		return result;
	}

	private static class AllocationFailure extends Warning {

		final TypeReference t;

		AllocationFailure(TypeReference t) {
			super(Warning.SEVERE);
			this.t = t;
		}

		@Override
		public String getMsg() {
			return getClass().toString() + " : " + t;
		}

		public static AllocationFailure create(TypeReference t) {
			return new AllocationFailure(t);
		}
	}
	
	/**
	 * @return true iff m is the fake root method.
	 * @throws IllegalArgumentException if m is null
	 */
	public static boolean isFakeRootMethod(MemberReference m) {
		if (m == null) {
			throw new IllegalArgumentException("m is null");
		}
		return m.equals(rootMethod);
	}

	/**
	 * @return true iff block is a basic block in the fake root method
	 * @throws IllegalArgumentException if block is null
	 */
	public static boolean isFromFakeRoot(IBasicBlock block) {
		if (block == null) {
			throw new IllegalArgumentException("block is null");
		}
		IMethod m = block.getMethod();
		return FakeRootMethod.isFakeRootMethod(m.getReference());
	}

	public static MethodReference getRootMethod() {
		return rootMethod;
	}

}
