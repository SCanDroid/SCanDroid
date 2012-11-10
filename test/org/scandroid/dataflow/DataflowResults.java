package org.scandroid.dataflow;

import java.util.Map;
import java.util.Set;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class DataflowResults {
    private static final String ost = "org.scandroid.testing.";
    
	public final Map<String, Set<String>> expectedResults = Maps.newHashMap();
    public DataflowResults() {

        Set<String> cargFlows = Sets.newHashSet();
        cargFlows.add("arg(0) -> ret");
        expectedResults.put(ost+"ConstructorArgFlow.flow(Ljava/lang/String;)Lorg/scandroid/testing/ConstructorArgFlow$Id;", cargFlows);
        expectedResults.put(ost+"ConstructorArgFlow.manualFlow(Ljava/lang/String;)Lorg/scandroid/testing/ConstructorArgFlow$Id;", cargFlows);
        expectedResults.put(ost+"ConstructorArgFlow.fieldAccessFlow(Lorg/scandroid/testing/ConstructorArgFlow$Id;)Ljava/lang/Object;", cargFlows);
        expectedResults.put(ost+"ConstructorArgFlow.getterAccessFlow(Lorg/scandroid/testing/ConstructorArgFlow$Id;)Ljava/lang/Object;", cargFlows);


        Set<String> echoFlows = Sets.newHashSet();
        echoFlows.add("arg(1) -> ret");
        expectedResults.put(ost+"EchoTest.echo(Ljava/lang/Object;)Ljava/lang/Object;", echoFlows);
        Set<String> staticEchoFlows = Sets.newHashSet();
        staticEchoFlows.add("arg(0) -> ret");
        expectedResults.put(ost+"EchoTest.static_echo(Ljava/lang/Object;)Ljava/lang/Object;", staticEchoFlows);
        expectedResults.put(ost+"EchoTest.echoTest(Ljava/lang/String;)Ljava/lang/Object;", staticEchoFlows);
        
        
        String fieldFlow_Pair = "Lorg/scandroid/testing/FieldFlows$Pair;";
        
		expectedResults.put(ost+"FieldFlows.dropFstStatic("+fieldFlow_Pair+")Ljava/lang/Object;",
        		Sets.newHashSet("arg(0) -> ret"));
		expectedResults.put(ost+"FieldFlows.swapStatic("+fieldFlow_Pair+")"+fieldFlow_Pair,
        		Sets.newHashSet("arg(0) -> ret"));
        expectedResults.put(ost+"FieldFlows.mkPairStatic(" +
        		"Lorg/scandroid/testing/FieldFlows$X;" +
        		"Lorg/scandroid/testing/FieldFlows$Y;)" + fieldFlow_Pair,
        		Sets.newHashSet("arg(0) -> ret", "arg(1) -> ret"));
        
		expectedResults.put(ost+"FieldFlows.dropFst("+fieldFlow_Pair+")Ljava/lang/Object;",
        		Sets.newHashSet("arg(1) -> ret"));
        expectedResults.put(ost+"FieldFlows.mkPair(" +
        		"Lorg/scandroid/testing/FieldFlows$X;" +
        		"Lorg/scandroid/testing/FieldFlows$Y;)" + fieldFlow_Pair,
        		Sets.newHashSet("arg(1) -> ret", "arg(2) -> ret"));
		expectedResults.put(ost+"FieldFlows.swap("+fieldFlow_Pair+")"+fieldFlow_Pair,
        		Sets.newHashSet("arg(1) -> ret"));
		
		expectedResults.put(ost+"FieldFlows.throughField(Ljava/lang/String;)I",
        		Sets.newHashSet("arg(1) -> ret"));
    }
}
