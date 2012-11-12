package org.scandroid.dataflow;

import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class DataflowResults {
	private static final Logger logger = LoggerFactory
			.getLogger(DataflowResults.class);
	
    private static final String ost = "org.scandroid.testing.";
    
	private final Map<String, Set<String>> expectedResults = Maps.newHashMap();

	public DataflowResults() {
        expectedResults.putAll(constructorArgFlow());
        expectedResults.putAll(echoFlows());
        expectedResults.putAll(fieldFlows());
        expectedResults.putAll(arrayLengthIntegerTest());
        expectedResults.putAll(arrayLengthTest());
        expectedResults.putAll(arrayLoad());
        expectedResults.putAll(assignmentReturnValFlow());
        expectedResults.putAll(deepFields());
        expectedResults.putAll(exceptionFlow());
        expectedResults.putAll(fieldAccessTest());
        expectedResults.putAll(globalStatics());
    }
    
    private Map<String, Set<String>> constructorArgFlow() {
    	Map<String, Set<String>> oracle = Maps.newHashMap();
    	
        Set<String> cargFlows = Sets.newHashSet();
        cargFlows.add("arg(0) -> ret");
        oracle.put(ost+"ConstructorArgFlow.flow(Ljava/lang/String;)Lorg/scandroid/testing/ConstructorArgFlow$Id;", cargFlows);
        oracle.put(ost+"ConstructorArgFlow.manualFlow(Ljava/lang/String;)Lorg/scandroid/testing/ConstructorArgFlow$Id;", cargFlows);
        oracle.put(ost+"ConstructorArgFlow.fieldAccessFlow(Lorg/scandroid/testing/ConstructorArgFlow$Id;)Ljava/lang/Object;", cargFlows);
        oracle.put(ost+"ConstructorArgFlow.getterAccessFlow(Lorg/scandroid/testing/ConstructorArgFlow$Id;)Ljava/lang/Object;", cargFlows);
    	
    	return oracle;
    }

	private Map<String, Set<String>> echoFlows() {
    	Map<String, Set<String>> oracle = Maps.newHashMap();

    	Set<String> echoFlows = Sets.newHashSet();
    	        
        echoFlows.add("arg(1) -> ret");
        oracle.put(ost+"EchoTest.echo(Ljava/lang/Object;)Ljava/lang/Object;", echoFlows);
        Set<String> staticEchoFlows = Sets.newHashSet();
        staticEchoFlows.add("arg(0) -> ret");
        oracle.put(ost+"EchoTest.static_echo(Ljava/lang/Object;)Ljava/lang/Object;", staticEchoFlows);
        oracle.put(ost+"EchoTest.echoTest(Ljava/lang/String;)Ljava/lang/Object;", staticEchoFlows);
    	
    	return oracle;
    
    }

	private Map<String, Set<String>> fieldFlows() {
    	Map<String, Set<String>> oracle = Maps.newHashMap();
    	
        String fieldFlow_Pair = "Lorg/scandroid/testing/FieldFlows$Pair;";
        
		oracle.put(ost+"FieldFlows.dropFstStatic("+fieldFlow_Pair+")Ljava/lang/Object;",
        		Sets.newHashSet("arg(0) -> ret"));
		oracle.put(ost+"FieldFlows.swapStatic("+fieldFlow_Pair+")"+fieldFlow_Pair,
        		Sets.newHashSet("arg(0) -> ret"));
        oracle.put(ost+"FieldFlows.mkPairStatic(" +
        		"Lorg/scandroid/testing/FieldFlows$X;" +
        		"Lorg/scandroid/testing/FieldFlows$Y;)" + fieldFlow_Pair,
        		Sets.newHashSet("arg(0) -> ret", "arg(1) -> ret"));
        
		oracle.put(ost+"FieldFlows.dropFst("+fieldFlow_Pair+")Ljava/lang/Object;",
        		Sets.newHashSet("arg(1) -> ret"));
        oracle.put(ost+"FieldFlows.mkPair(" +
        		"Lorg/scandroid/testing/FieldFlows$X;" +
        		"Lorg/scandroid/testing/FieldFlows$Y;)" + fieldFlow_Pair,
        		Sets.newHashSet("arg(1) -> ret", "arg(2) -> ret"));
		oracle.put(ost+"FieldFlows.swap("+fieldFlow_Pair+")"+fieldFlow_Pair,
        		Sets.newHashSet("arg(1) -> ret"));
		
		oracle.put(ost+"FieldFlows.throughField(Ljava/lang/String;)I",
        		Sets.newHashSet("arg(1) -> ret"));
    	
    	return oracle;
    }
    
    private Map<String, Set<String>> arrayLengthIntegerTest() {
    	Map<String, Set<String>> oracle = Maps.newHashMap();
    	             // ArrayLengthIntegerTest.main([Ljava/lang/String;)Ljava/lang/Object;
    	oracle.put(ost+"ArrayLengthIntegerTest.main([Ljava/lang/String;)Ljava/lang/Object;",
    			Sets.newHashSet("arg(0) -> ret"));
    	
    	return oracle;
    }
    
    private Map<String, Set<String>> arrayLengthTest() {
    	Map<String, Set<String>> oracle = Maps.newHashMap();
    	
    	oracle.put(ost+"ArrayLengthTest.main([Ljava/lang/String;)Ljava/lang/Object;",
    			Sets.newHashSet("arg(0) -> ret"));
    	return oracle;
    }
    
    private Map<String, Set<String>> arrayLoad() {
    	Map<String, Set<String>> oracle = Maps.newHashMap();
    	
    	oracle.put(ost+"ArrayLoad.arrayFlow([Ljava/lang/String;)Ljava/lang/String;",
    			Sets.newHashSet("arg(0) -> ret"));
    	
    	oracle.put(ost+"ArrayLoad.copyElement([Ljava/lang/String;)Ljava/lang/String;",
    			Sets.newHashSet("arg(1) -> ret"));
    	return oracle;
    }
    
    private Map<String, Set<String>> assignmentReturnValFlow() {
    	Map<String, Set<String>> oracle = Maps.newHashMap();
    	
    	oracle.put(ost+"AssignmentReturnValFlow.assignVal" +
    			        "([Ljava/lang/String;)Ljava/lang/String;",
    			   Sets.newHashSet("arg(0) -> ret"));
    	
    	return oracle;
    }
    
    private Map<String, Set<String>> deepFields() {
    	Map<String, Set<String>> oracle = Maps.newHashMap();
    	
    	oracle.put(ost+"DeepFields.test(" +
    			        "Lorg/scandroid/testing/DeepFields$Foo;" +
    			        "Ljava/lang/Object;)V",
    			   Sets.newHashSet("arg(1) -> arg(0)"));
    	
    	return oracle;
    }
    
    private Map<String, Set<String>> exceptionFlow() {
    	Map<String, Set<String>> oracle = Maps.newHashMap();
    	
    	oracle.put(ost+"ExceptionFlow.thrower(" +
    			        "Ljava/lang/String;)V",
    			   Sets.newHashSet("arg(0) -> exception"));
    	
    	return oracle;
    }
    
    private Map<String, Set<String>> fieldAccessTest() {
    	Map<String, Set<String>> oracle = Maps.newHashMap();
    	
    	oracle.put(ost+"FieldAccessTest.getStr()Ljava/lang/String;",
    				// actually returns a static field:
    			   Sets.newHashSet("arg(0) -> ret"));

    	oracle.put(ost+"FieldAccessTest.getClassField()Ljava/lang/String;",
 			   Sets.newHashSet("arg(0) -> ret"));

    	return oracle;
    }
    
    private Map<String, Set<String>> globalStatics() {
    	Map<String, Set<String>> oracle = Maps.newHashMap();
    	
    	oracle.put(ost+"GlobalStatics.getFoo()Ljava/lang/Integer;",
    				// returns a static field:
    			   Sets.newHashSet("????-> ret"));

    	oracle.put(ost+"GlobalStatics.setFoo(Ljava/lang/Integer;)V",
 			   Sets.newHashSet("arg(0) -> ????"));

    	return oracle;
    }

	public Set<String> getFlows(String signature) {
		Set<String> flows = expectedResults.get(signature);
		logger.debug("getting flows for: "+signature+" found: "+flows);
		return flows;
	}

	/**
	 * Check to see if the method descriptor is listed in the oracle.
	 * 
	 * @param desc
	 * @return True if there is a known flow for the given descriptor, false if note.
	 */
	public boolean describesFlow(String desc) {
		return expectedResults.containsKey(desc);
	}

	/**
	 * Get the set of methods that have defined flows. 
	 * 
	 * @return
	 */
	public Set<String> expectedMethods() {
		return Sets.newHashSet(expectedResults.keySet());
	}
}
