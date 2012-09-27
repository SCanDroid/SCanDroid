package policy;

import java.util.Map;
import java.util.Set;

import flow.types.FlowType;

class PolicyChecker {
    private final PolicySpec spec;

    PolicyChecker(PolicySpec spec) {
        this.spec = spec;
    }

    boolean flowsSatisfyPolicy(Map<FlowType, Set<FlowType>> flows) {
        for(FlowType src : flows.keySet()) {
            for(FlowType dest : flows.get(src)) {
                if(!spec.allowed(src, dest)) return false;
            }
        }
        return true;
    }
}
