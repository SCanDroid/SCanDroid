package policy;

import flow.types.FlowType;

public interface PolicySpec {
    boolean allowed(FlowType src, FlowType dest);
}
