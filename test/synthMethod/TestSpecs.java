package synthMethod;

import spec.CallArgSinkSpec;
import spec.EntryArgSourceSpec;
import spec.ISpecs;
import spec.MethodNamePattern;
import spec.SinkSpec;
import spec.SourceSpec;

public class TestSpecs implements ISpecs {
    @Override
    public MethodNamePattern[] getEntrypointSpecs() {
        return null;
    }
    @Override
    public SourceSpec[] getSourceSpecs() {
        return new SourceSpec[] { 
                 new EntryArgSourceSpec(new MethodNamePattern(
                   "Lorg/scandroid/testing/App", "main"),
                   new int[] { 0 })
                 };
    }

    @Override
    public SinkSpec[] getSinkSpecs() {
        return new SinkSpec[] { 
                new CallArgSinkSpec(new MethodNamePattern(
                  "Lorg/scandroid/testing/SourceSink", "sink"), new int[] {0}) };
    }

}
