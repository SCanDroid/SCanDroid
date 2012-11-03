package org.scandroid.flow.functions;

import org.scandroid.domain.CodeElement;

final class UseDefPair
{
    private final CodeElement use;
    private final CodeElement def;
    public UseDefPair(CodeElement use, CodeElement def) {
        this.use = use;
        this.def = def;
    }
    public CodeElement getUse() {
        return use;
    }
    public CodeElement getDef() {
        return def;
    }
}