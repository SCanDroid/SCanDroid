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
        expectedResults.put("org.scandroid.testing.ConstructorArgFlow.flow(Ljava/lang/String;)Lorg/scandroid/testing/ConstructorArgFlow$Id;", cargFlows);
    }
}
