package model;

import static util.MyLogger.log;
import static util.MyLogger.LogLevel.DEBUG;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Stack;

import com.ibm.wala.classLoader.ArrayClass;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.Language;
import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ipa.summaries.MethodSummary;
import com.ibm.wala.shrikeBT.IInvokeInstruction;
import com.ibm.wala.shrikeBT.IInvokeInstruction.IDispatch;
import com.ibm.wala.ssa.ConstantValue;
import com.ibm.wala.ssa.SSAArrayStoreInstruction;
import com.ibm.wala.ssa.SSAInstructionFactory;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSANewInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.Descriptor;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.collections.HashMapFactory;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.debug.UnimplementedError;
import com.ibm.wala.util.strings.Atom;
import com.ibm.wala.util.warnings.Warnings;

import spec.AndroidSpecs;
import spec.ISpecs;
import spec.MethodNamePattern;
import util.AndroidAppLoader;
import util.LoaderUtils;

public class AppModelMethod {
	int nextLocal;
    /**
     * A mapping from String (variable name) -> Integer (local number)
     */
    private Map<String, Integer> symbolTable = null;
    
    private MethodSummary methodSummary;
    
    private final IClassHierarchy cha;
    
    private final AnalysisScope scope;
    
    private Map<ConstantValue, Integer> constant2ValueNumber = HashMapFactory.make();
    
    
    
    SSAInstructionFactory insts;

    
	//Maps a Type to variable name
	private Map<TypeReference, Integer> typeToID = new HashMap<TypeReference, Integer> ();    
	//innerclass dependencies
	private Map<TypeReference, LinkedList<TypeReference>> icDependencies = new HashMap<TypeReference, LinkedList<TypeReference>> ();
    //all callbacks to consider
	private List<MethodParams> callBacks = new ArrayList<MethodParams>();
	
	private class MethodParams{
		public IMethod im;
		public int params[];
		private MethodParams(IMethod method) {
			im = method;
		}
		private void setParams(int p[]) {
			params = p;
		}
		private IMethod getIMethod() {
			return im;
		}
		private int[] getParams() {
			return params;
		}
	}
	
	public AppModelMethod(IClassHierarchy cha, AnalysisScope scope) {
    	this.cha = cha;
    	this.scope = scope;    	
	    Language lang = scope.getLanguage(ClassLoaderReference.Application.getLanguage());
	    insts = lang.instructionFactory();
		
		startMethod();
    	buildTypeMap();
		processTypeMap();
		processCallBackParams();
		createLoopAndSwitch();
    }

	private void createLoopAndSwitch() {
		int callbackSize = callBacks.size();
		//start of while loop
		int loopLabel = methodSummary.getStatements().length;
		int switchValue = nextLocal++;
		//default label, for now same as case1
		int defLabel = loopLabel+1;
		int[] casesAndLabels = new int[2*callbackSize];
        
        for (int i = 0; i < callbackSize; i++) {
        	casesAndLabels[i*2] = i+1;
        	casesAndLabels[i*2+1] = defLabel+i*2;
        }
        
        methodSummary.addStatement(
        		insts.SwitchInstruction(switchValue, defLabel, casesAndLabels));
        
        for (int i = 0; i < callbackSize; i++) {
        	MethodParams mp = callBacks.get(i);
        	IMethod im = mp.getIMethod();
        	IDispatch dispatch;
        	if (im.isInit()) {
        		dispatch = IInvokeInstruction.Dispatch.SPECIAL;
        	}
        	else if (im.isAbstract()) {
        		dispatch = IInvokeInstruction.Dispatch.INTERFACE;
        	}
        	else if (im.isStatic()) {
        		dispatch = IInvokeInstruction.Dispatch.STATIC;
        	}
        	else
        		dispatch = IInvokeInstruction.Dispatch.VIRTUAL;
        	addInvocation(mp.getParams(), 
        			CallSiteReference.make(methodSummary.getStatements().length, 
        					mp.getIMethod().getReference(), 
        					dispatch));        	
        	methodSummary.addStatement(insts.GotoInstruction(loopLabel));
        }

		
		
	}
	
	private void startMethod() {
    	String className = "Lcom/SCanDroid/AppModel";
    	String methodName = "entry";
    	TypeReference governingClass = 
				TypeReference.findOrCreate(ClassLoaderReference.Application, TypeName.string2TypeName(className));
		Atom mName = Atom.findOrCreateUnicodeAtom(methodName);
		Language lang = scope.getLanguage(ClassLoaderReference.Application.getLanguage());
		Descriptor D = Descriptor.findOrCreateUTF8(lang, "()V");
		MethodReference mref = MethodReference.findOrCreate(governingClass, mName, D);
	
		methodSummary = new MethodSummary(mref);
				
		methodSummary.setStatic(true);
		methodSummary.setFactory(false);
		
		int nParams = mref.getNumberOfParameters();
		nextLocal = nParams + 1;
		symbolTable = HashMapFactory.make(5);
		for (int i = 0; i < nParams; i++) {
			symbolTable.put("arg" + i, new Integer(i + 1));
		}
    }	
    
    private void buildTypeMap() {
		//Go through all possible callbacks found in Application code
		//Add their TypeReference with a unique variable name to typeToID.
		//Also keep track of all innerclasses found.
    	for (MethodNamePattern mnp:AndroidSpecs.callBacks) {
    		for (IMethod im: mnp.getPossibleTargets(cha)) {
    			// limit to functions defined within the application
    			if(LoaderUtils.fromLoader(im, ClassLoaderReference.Application))
    			{   
    				callBacks.add(new MethodParams(im));
    				TypeReference tr = im.getDeclaringClass().getReference(); 
    				if (!typeToID.containsKey(tr)) {
    					//class is an innerclass
    					if (tr.getName().getClassName().toString().contains("$")) {
    						addDependencies(tr);
    					}
    					log(DEBUG,"AppModel Mapping type "+tr.getName()+" to name " + nextLocal);
    					typeToID.put(tr, nextLocal++);        				
    				}
    			}
    		}
    	}
    	
    	//Go through innerclasses, and make sure outerclasses 
    	//are in typeToID list, if not add them.
    	for (Entry<TypeReference, LinkedList<TypeReference>> trE:icDependencies.entrySet()) {
    		for (TypeReference trS: trE.getValue()) {
        		if (!typeToID.containsKey(trS)) {
					log(DEBUG,"AppModel Mapping type "+trS.getName()+" to name " + nextLocal);
        			typeToID.put(trS, nextLocal++);
        		}
    		}    		
    	}
    }

        
    private void processTypeMap() {
    	Set<Integer> createdIDs = new HashSet<Integer> ();    	
    	for (Entry<TypeReference, Integer> eSet:typeToID.entrySet()) {
    		TypeReference tr = eSet.getKey();
    		String className = tr.getName().getClassName().toString();
    		//Not an anonymous innerclass
    		if (!className.contains("$")) {
        		Integer i = eSet.getValue();
        		if (createdIDs.contains(i))
        			continue;
    			processAllocation(tr, i, false);
    			createdIDs.add(i);
    		}
    		//Is an anonymous innerclass
    		else {
    			for (TypeReference trD:icDependencies.get(tr)) {
    				Integer i = typeToID.get(trD);
    				if (!createdIDs.contains(i)) {
    					processAllocation(trD,i, true);
    					createdIDs.add(i);
    				}
    			}
    		}
    	}
    	
    	assert(createdIDs.size() == typeToID.size()):"typeToID and createdID size do not match";    	
    }
    
    private void processCallBackParams() {
    	for (MethodParams mp:callBacks) {
    		int params[] = new int[mp.getIMethod().getNumberOfParameters()];
    		int startPos;
    		if (mp.getIMethod().isStatic()) {
    			startPos = 0;
    		}
    		else {
    			params[0] = typeToID.get(mp.getIMethod().getDeclaringClass().getReference());
    			startPos = 1;    			
    		}
    		for (int i = startPos; i < params.length; i++) {
    			params[i] = makeArgument(mp.getIMethod().getParameterType(i));
    		}
    		mp.setParams(params);    		
    	}    	    
    }
    
    private int makeArgument(TypeReference tr) {
    	if (tr.isPrimitiveType())
    		return addLocal();
    	else {
    		SSANewInstruction n = processAllocation(tr, nextLocal++, false);
    		return (n == null) ? -1 : n.getDef();
    	}
    }   
    
    private int addLocal() {
    	return nextLocal++;
    }
    
    private SSANewInstruction processAllocation (TypeReference tr, Integer i, boolean isInner) {
        // create the allocation statement and add it to the method summary
        NewSiteReference ref = NewSiteReference.make(methodSummary.getStatements().length, tr);        
        SSANewInstruction a = null;
        
        if (tr.isArrayType()) {
        	int[] sizes = new int[((ArrayClass)cha.lookupClass(tr)).getDimensionality()];
        	Arrays.fill(sizes, getValueNumberForIntConstant(1));
        	a = insts.NewInstruction(i, ref, sizes);
        } else {
        	a = insts.NewInstruction(i, ref);
        }
        
        methodSummary.addStatement(a);
        
        IClass klass = cha.lookupClass(tr);
        if (klass == null) {
          return null;
        }
        
        if (klass.isArrayClass()) {
        	int arrayRef = a.getDef();
        	TypeReference e = klass.getReference().getArrayElementType();
        	while (e != null && !e.isPrimitiveType()) {
        		// allocate an instance for the array contents
        		NewSiteReference n = NewSiteReference.make(methodSummary.getStatements().length, e);
        		int alloc = nextLocal++;
        		SSANewInstruction ni = null;
        		if (e.isArrayType()) {
        			int[] sizes = new int[((ArrayClass)cha.lookupClass(tr)).getDimensionality()];
        			Arrays.fill(sizes, getValueNumberForIntConstant(1));
        			ni = insts.NewInstruction(alloc, n, sizes);
        		} else {
        			ni = insts.NewInstruction(alloc, n);
        		}
        		methodSummary.addStatement(ni);

        		// emit an astore
        		SSAArrayStoreInstruction store = insts.ArrayStoreInstruction(arrayRef, getValueNumberForIntConstant(0), alloc, e);
        		methodSummary.addStatement(store);

        		e = e.isArrayType() ? e.getArrayElementType() : null;
        		arrayRef = alloc;
        	}
        }
        
        //invoke constructor 
        IMethod ctor = cha.resolveMethod(klass, MethodReference.initSelector);
        
        if (ctor != null) {
        	//only check for more constructors when we're looking through the inner classes?
			if (isInner && !ctor.getDeclaringClass().getName().toString().equals(klass.getName().toString())) {
				boolean foundValidCtor = false;
				for (IMethod im: klass.getAllMethods()) {
					if (im.getDeclaringClass().getName().toString().equals(klass.getName().toString()) && 
							im.getSelector().getName().toString().equals(MethodReference.initAtom.toString()) &&
							im.getDescriptor().getNumberOfParameters()==1 &&
							im.getDescriptor().getParameters()[0].equals(
									icDependencies.get(tr).get(icDependencies.get(tr).size()-2).getName())) {
						ctor = im;
						foundValidCtor = true;
						break;						
					}
				}
				if (!foundValidCtor)
					throw new UnimplementedError("Check for other constructors, or just use default Object constructor");
			}
			int[] params;
			if (ctor.getDescriptor().getNumberOfParameters() == 0)
				params = new int[] {i};
			else {
				params = new int[] {i, typeToID.get(icDependencies.get(tr).get(icDependencies.get(tr).size()-2))};
			}
        	addInvocation(params, CallSiteReference.make(methodSummary.getStatements().length, ctor.getReference(),
        			IInvokeInstruction.Dispatch.SPECIAL));
        }

        return a;
    }
        
    public SSAInvokeInstruction addInvocation(int[] params, CallSiteReference site) {
    	if (site == null) {
    		throw new IllegalArgumentException("site is null");
    	}
    	CallSiteReference newSite = CallSiteReference.make(methodSummary.getStatements().length, site.getDeclaredTarget(), site.getInvocationCode());
    	SSAInvokeInstruction s = null;
    	if (newSite.getDeclaredTarget().getReturnType().equals(TypeReference.Void)) {
    		s = insts.InvokeInstruction(params, nextLocal++, newSite);
    	} else {
    		s = insts.InvokeInstruction(nextLocal++, params, nextLocal++, newSite);
    	}
    	methodSummary.addStatement(s);
    	//        	cache.invalidate(this, Everywhere.EVERYWHERE);
    	return s;
    }
          


  	private void addDependencies(TypeReference tr) {
		String packageName = "L"+tr.getName().getPackage().toString()+"/";
		String outerClassName;		
		String innerClassName = tr.getName().getClassName().toString();
		LinkedList<TypeReference> trLL = new LinkedList<TypeReference> ();
		trLL.push(tr);
		int index = innerClassName.lastIndexOf("$");
		while (index != -1) {
    		outerClassName = innerClassName.substring(0, index);
    		TypeReference innerTR = TypeReference.findOrCreate(ClassLoaderReference.Application, packageName+outerClassName);
    		trLL.push(innerTR);
    		
    		innerClassName = outerClassName;
    		index = innerClassName.lastIndexOf("$");
		}
		icDependencies.put(tr, trLL);				
	}
  	
    protected int getValueNumberForIntConstant(int c) {
        ConstantValue v = new ConstantValue(c);
        Integer result = constant2ValueNumber.get(v);
        if (result == null) {
          result = nextLocal++;
          constant2ValueNumber.put(v, result);
        }
        return result;
      }
}
