package org.scandroid.dataflow;

import java.util.Map;
import java.util.Set;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class DataflowResults {
    public final Map<String, Set<String>> expectedResults = Maps.newHashMap();
    public DataflowResults() {
        // TODO: populate expectedResults
        Set<String> cargFlows = Sets.newHashSet();
        cargFlows.add("arg(0) -> ret");
        expectedResults.put("org.scandroid.testing.EchoTest.echo(Ljava/lang/Object;)Ljava/lang/Object;", cargFlows);
        expectedResults.put("org.scandroid.testing.ConstructorArgFlow.flow(Ljava/lang/String;)Lorg/scandroid/testing/ConstructorArgFlow$Id;", cargFlows);
        expectedResults.put("org.scandroid.testing.ConstructorArgFlow.manualFlow(Ljava/lang/String;)Lorg/scandroid/testing/ConstructorArgFlow$Id;", cargFlows);
        expectedResults.put("org.scandroid.testing.ConstructorArgFlow.fieldAccessFlow(Lorg/scandroid/testing/ConstructorArgFlow$Id;)Ljava/lang/Object;", cargFlows);
        expectedResults.put("org.scandroid.testing.ConstructorArgFlow.getterAccessFlow(Lorg/scandroid/testing/ConstructorArgFlow$Id;)Ljava/lang/Object;", cargFlows);
    }
}
