package policy;

import java.util.Map;
import java.util.Set;

import com.ibm.wala.ssa.ISSABasicBlock;

import flow.types.FlowType;

public class PolicyChecker <E extends ISSABasicBlock> {
    private final PolicySpec spec;

    public PolicyChecker(PolicySpec spec) {
        this.spec = spec;
    }

    public boolean flowsSatisfyPolicy(Map<FlowType<E>, Set<FlowType<E>>> flows) {
        for(FlowType src : flows.keySet()) {
            for(FlowType dest : flows.get(src)) {
                if(!spec.allowed(src, dest)) return false;
            }
        }
        return true;
    }
}
