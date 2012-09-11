package synthMethod;

import spec.CallArgSinkSpec;
import spec.EntryArgSourceSpec;
import spec.ISpecs;
import spec.MethodNamePattern;
import spec.SinkSpec;
import spec.SourceSpec;

public class TestSpecs implements ISpecs {
	/*
	private SourceSpec[] sources;
	private SinkSpec[] sinks;
	

	public TestSpecs(Collection<CGNode> nodes) {
	    
	    // Just create EntryArgSourceSpecs for a specific method,
	    // and CallArgSourceSpecs for a different method.
	    // Those should be sufficient to establish a data flow we can use for 
	    /// testing.
	    
		ArrayList<SourceSpec> sourcesAL = new ArrayList<SourceSpec>();
		ArrayList<SinkSpec> sinksAL = new ArrayList<SinkSpec>();

        for (CGNode node:nodes) {
        	String nodeClass = node.getMethod().getDeclaringClass().getName().toString();
        	String nodeMethod = node.getMethod().getName().toString();
        	MethodNamePattern mnp = new MethodNamePattern(nodeClass, nodeMethod);
        	FieldNamePattern fnp = new FieldNamePattern(nodeClass, nodeMethod);
        	
        	sourcesAL.add(new EntryArgSourceSpec(mnp, new int[] {}));
        	sourcesAL.add(new FieldSourceSpec(fnp));
        	sinksAL.add(new EntryRetSinkSpec(mnp));
        	sinksAL.add(new FieldSinkSpec(fnp));

        }        
        sources = sourcesAL.toArray(new SourceSpec[sourcesAL.size()]);        
        sinks = sinksAL.toArray(new SinkSpec[sinksAL.size()]);

	}
	*/

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
