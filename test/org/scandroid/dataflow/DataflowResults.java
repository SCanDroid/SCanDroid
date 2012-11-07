package org.scandroid.dataflow;

import java.util.Map;
import java.util.Set;

import org.scandroid.flow.types.FlowType;

import com.google.common.collect.Maps;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;

public class DataflowResults {
    public final Map<String, Map<FlowType<IExplodedBasicBlock>, Set<FlowType<IExplodedBasicBlock>>>> expectedResults =
            Maps.newHashMap();
    public DataflowResults() {
        // TODO: populate expectedResults
        expectedResults.put("org.scandroid.testing.ConstructorArgFlow.flow(Ljava/lang/String;)Lorg/scandroid/testing/ConstructorArgFlow$Id;", null); // TODO: entryarg(0) -> sink
    }
}
